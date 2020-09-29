package io.radanalytics.operator.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.radanalytics.operator.SDKEntrypoint;
import io.radanalytics.operator.common.crd.InfoClass;
import io.radanalytics.operator.common.crd.InfoClassDoneable;
import io.radanalytics.operator.common.crd.InfoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.radanalytics.operator.common.AnsiColors.*;

public abstract class AbstractWatcher<T extends EntityInfo> {

    protected static final Logger log = LoggerFactory.getLogger(AbstractWatcher.class.getName());

    private final boolean isCrd;
    private final String namespace;
    private final String entityName;
    private final KubernetesClient client;
    private final CustomResourceDefinition crd;
    private final Map<String, String> selector;

    private final BiConsumer<T, String> onAdd;
    private final BiConsumer<T, String> onDelete;
    private final BiConsumer<T, String> onModify;

    private final Predicate<ConfigMap> isSupported;
    private final Function<ConfigMap, T> convert;
    private final Function<InfoClass, T> convertCr;

    private volatile Watch watch;
    protected volatile boolean fullReconciliationRun = false;

    // use via builder
    protected AbstractWatcher(boolean isCrd, String namespace, String entityName, KubernetesClient client,
                              CustomResourceDefinition crd, Map<String, String> selector, BiConsumer<T, String> onAdd,
                              BiConsumer<T, String> onDelete, BiConsumer<T, String> onModify, Predicate<ConfigMap> isSupported,
                              Function<ConfigMap, T> convert, Function<InfoClass, T> convertCr) {
        this.isCrd = isCrd;
        this.namespace = namespace;
        this.entityName = entityName;
        this.client = client;
        this.crd = crd;
        this.selector = selector;
        this.onAdd = onAdd;
        this.onDelete = onDelete;
        this.onModify = onModify;
        this.isSupported = isSupported;
        this.convert = convert;
        this.convertCr = convertCr;
    }

    public abstract CompletableFuture<? extends AbstractWatcher<T>> watch();

    protected CompletableFuture<Watch> createConfigMapWatch() {
        CompletableFuture<Watch> cf = CompletableFuture.supplyAsync(() -> {
            MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> aux = client.configMaps();

            final boolean inAllNs = "*".equals(namespace);
            Watchable<Watch, Watcher<ConfigMap>> watchable = inAllNs ? aux.inAnyNamespace().withLabels(selector) :
                    aux.inNamespace(namespace).withLabels(selector);
            Watch watch = watchable.watch(new Watcher<ConfigMap>() {
                @Override
                public void eventReceived(Action action, ConfigMap cm) {
                    if (isSupported.test(cm)) {
                        log.info("ConfigMap in namespace {} was {}\nCM:\n{}\n", namespace, action, cm);
                        T entity = convert.apply(cm);
                        if (entity == null) {
                            log.error("something went wrong, unable to parse {} definition", entityName);
                        }
                        if (action.equals(Action.ERROR)) {
                            log.error("Failed ConfigMap {} in namespace{} ", cm, namespace);
                        } else {
                            handleAction(action, entity, inAllNs ? cm.getMetadata().getNamespace() : namespace);
                        }
                    } else {
                        log.error("Unknown CM kind: {}", cm.toString());
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    if (e != null) {
                        log.error("Watcher closed with exception in namespace {}", namespace, e);
                        recreateWatcher();
                    } else {
                        log.info("Watcher closed in namespace {}", namespace);
                    }
                }
            });
            return watch;
        }, SDKEntrypoint.getExecutors());
        cf.thenApply(w -> {
            log.info("ConfigMap watcher running for labels {}", selector);
            return w;
        }).exceptionally(e -> {
            log.error("ConfigMap watcher failed to start", e.getCause());
            return null;
        });
        return cf;
    }

    protected CompletableFuture<Watch> createCustomResourceWatch() {
        CompletableFuture<Watch> cf = CompletableFuture.supplyAsync(() -> {
            MixedOperation<InfoClass, InfoList, InfoClassDoneable, Resource<InfoClass, InfoClassDoneable>> aux =
                    client.customResources(crd, InfoClass.class, InfoList.class, InfoClassDoneable.class);

            final boolean inAllNs = "*".equals(namespace);
            Watchable<Watch, Watcher<InfoClass>> watchable = inAllNs ? aux.inAnyNamespace() : aux.inNamespace(namespace);
            Watch watch = watchable.watch(new Watcher<InfoClass>() {
                @Override
                public void eventReceived(Action action, InfoClass info) {
                    log.info("Custom resource in namespace {} was {}\nCR:\n{}", namespace, action, info);
                    T entity = convertCr.apply(info);
                    if (entity == null) {
                        log.error("something went wrong, unable to parse {} definition", entityName);
                    }
                    if (action.equals(Action.ERROR)) {
                        log.error("Failed Custom resource {} in namespace{} ", info, namespace);
                    } else {
                        handleAction(action, entity, inAllNs ? info.getMetadata().getNamespace() : namespace);
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    if (e != null) {
                        log.error("Watcher closed with exception in namespace {}", namespace, e);
                        recreateWatcher();
                    } else {
                        log.info("Watcher closed in namespace {}", namespace);
                    }
                }
            });
            AbstractWatcher.this.watch = watch;
            return watch;
        }, SDKEntrypoint.getExecutors());
        cf.thenApply(w -> {
            log.info("CustomResource watcher running for kinds {}", entityName);
            return w;
        }).exceptionally(e -> {
            log.error("CustomResource watcher failed to start", e.getCause());
            return null;
        });
        return cf;
    }

    private void recreateWatcher() {
        this.watch.close();
        CompletableFuture<Watch> configMapWatch = isCrd ? createCustomResourceWatch() : createConfigMapWatch();
        final String crdOrCm = isCrd ? "CustomResource" : "ConfigMap";
        configMapWatch.thenApply(res -> {
            log.info("{} watch recreated in namespace {}", crdOrCm, namespace);
            this.watch = res;
            return res;
        }).exceptionally(e -> {
            log.error("Failed to recreate {} watch in namespace {}", crdOrCm, namespace);
            return null;
        });
    }

    private void handleAction(Watcher.Action action, T entity, String ns) {
        if (!fullReconciliationRun) {
            return;
        }
        String name = entity.getName();
        try {
            switch (action) {
                case ADDED:
                    log.info("{}creating{} {}:  \n{}\n", gr(), xx(), entityName, name);
                    onAdd.accept(entity, ns);
                    log.info("{} {} has been  {}created{}", entityName, name, gr(), xx());
                    break;
                case DELETED:
                    log.info("{}deleting{} {}:  \n{}\n", gr(), xx(), entityName, name);
                    onDelete.accept(entity, ns);
                    log.info("{} {} has been  {}deleted{}", entityName, name, gr(), xx());
                    break;
                case MODIFIED:
                    log.info("{}modifying{} {}:  \n{}\n", gr(), xx(), entityName, name);
                    onModify.accept(entity, ns);
                    log.info("{} {} has been  {}modified{}", entityName, name, gr(), xx());
                    break;
                default:
                    log.error("Unknown action: {} in namespace {}", action, namespace);
            }
        } catch (Exception e) {
            log.warn("{}Error{} when reacting on event, cause: {}", re(), xx(), e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        log.info("Stopping {} for namespace {}", isCrd ? "CustomResourceWatch" : "ConfigMapWatch", namespace);
        watch.close();
        client.close();
    }

    public void setFullReconciliationRun(boolean fullReconciliationRun) {
        this.fullReconciliationRun = fullReconciliationRun;
    }
}


