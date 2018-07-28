package io.radanalytics.operator.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.radanalytics.operator.Entrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.radanalytics.operator.common.AnsiColors.ANSI_G;
import static io.radanalytics.operator.common.AnsiColors.ANSI_RESET;

public abstract class AbstractOperator<T extends EntityInfo> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    protected final KubernetesClient client;
    private final Map<String, String> selector;
    private final String entityName;
    private final String operatorName;
    protected final String namespace;
    private final boolean isOpenshift;

    private volatile Watch configMapWatch;

    public AbstractOperator(String namespace,
                            boolean isOpenshift,
                            KubernetesClient client,
                            String entityName,
                            Map<String, String> selector) {
        this.namespace = namespace;
        this.isOpenshift = isOpenshift;
        this.client = client;
        this.entityName = entityName;
        this.selector = selector;
        this.operatorName = entityName + " Operator";
    }

    abstract protected void onAdd(T entity, boolean isOpenshift);

    abstract protected void onDelete(T entity, boolean isOpenshift);

    abstract protected void onModify(T entity, boolean isOpenshift);

    abstract protected boolean isSupported(ConfigMap cm);

    abstract protected T convert(ConfigMap cm);

    public String getName() {
        return operatorName;
    }

    public CompletableFuture<Watch> start() {
        log.info("Starting {} for namespace {}", operatorName, namespace);

        CompletableFuture<Watch> future = createConfigMapWatch();
        future.thenApply(res -> {
                this.configMapWatch = res;
                log.info("{} running for namespace {}", operatorName, namespace);
                return res;
        }).exceptionally(e -> {
            log.error("{} startup failed for namespace {}", operatorName, namespace, e.getCause());
            return null;
        });
        return future;
    }

    public void stop() {
        log.info("Stopping {} for namespace {}", operatorName, namespace);
        configMapWatch.close();
        client.close();
    }

    private CompletableFuture<Watch> createConfigMapWatch() {
        CompletableFuture<Watch> cf = CompletableFuture.supplyAsync(() -> {
            MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> aux = client.configMaps();
            Watchable<Watch, Watcher<ConfigMap>> watchable = "*".equals(namespace) ? aux.inAnyNamespace().withLabels(selector) : aux.inNamespace(namespace).withLabels(selector);
            Watch watch = watchable.watch(new Watcher<ConfigMap>() {
                @Override
                public void eventReceived(Action action, ConfigMap cm) {
                    if (isSupported(cm)) {
                        log.info("ConfigMap \n{}\n in namespace {} was {}", cm, namespace, action);
                        T entity = convert(cm);
                        if (entity == null) {
                            log.error("something went wrong, unable to parse {} definition", entityName);
                        }
                        String name = entity.getName();
                        switch (action) {
                            case ADDED:
                                log.info("{}creating{} {}:  \n{}\n", ANSI_G, ANSI_RESET, entityName, name);
                                onAdd(entity, isOpenshift);
                                log.info("{} {} has been {}created{}", entityName, name, ANSI_G, ANSI_RESET);
                                break;
                            case DELETED:
                                log.info("{}deleting{} {}:  \n{}\n", ANSI_G, ANSI_RESET, entityName, name);
                                onDelete(entity, isOpenshift);
                                log.info("{} {} has been {}deleted{}", entityName, name, ANSI_G, ANSI_RESET);
                                break;
                            case MODIFIED:
                                log.info("{}modifying{} {}:  \n{}\n", ANSI_G, ANSI_RESET, entityName, name);
                                onModify(entity, isOpenshift);
                                log.info("{} {} has been {}modified{}", entityName, name, ANSI_G, ANSI_RESET);
                                break;
                            case ERROR:
                                log.error("Failed ConfigMap {} in namespace{} ", cm, namespace);
                                break;
                            default:
                                log.error("Unknown action: {} in namespace {}", action, namespace);
                        }
                    } else {
                        log.error("Unknown CM kind: {}", cm.toString());
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    if (e != null) {
                        log.error("Watcher closed with exception in namespace {}", namespace, e);
                        recreateConfigMapWatch();
                    } else {
                        log.info("Watcher closed in namespace {}", namespace);
                    }
                }
            });
            return watch;
        }, Entrypoint.EXECUTORS);
        cf.thenApply(w -> {
            log.info("ConfigMap watcher running for labels {}", selector);
            return w;
        }).exceptionally(e -> {
            log.error("ConfigMap watcher failed to start", e.getCause());
            return null;
        });
        return cf;
    }

    private void recreateConfigMapWatch() {
        CompletableFuture<Watch> configMapWatch = createConfigMapWatch();
        configMapWatch.thenApply(res -> {
            log.info("ConfigMap watch recreated in namespace {}", namespace);
            this.configMapWatch = res;
            return res;
        }).exceptionally(e -> {
            log.error("Failed to recreate ConfigMap watch in namespace {}", namespace);
            return null;
        });
    }

}
