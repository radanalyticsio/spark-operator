package io.radanalytics.operator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.radanalytics.operator.resource.HasDataHelper;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class SparkOperator extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(SparkOperator.class.getName());

    private static final int HEALTH_SERVER_PORT = 8080;

    private final KubernetesClient client;
    private final Map<String, String> selector;
    private final String namespace;
    private final boolean isOpenshift;
    private final long reconciliationInterval;

    private volatile Watch configMapWatch;

    private long reconcileTimer;

    public SparkOperator(String namespace,
                         boolean isOpenshift,
                         long reconciliationInterval,
                         KubernetesClient client) {
        this.namespace = namespace;
        this.isOpenshift = isOpenshift;
        this.reconciliationInterval = reconciliationInterval;
        this.client = client;
        this.selector = LabelsHelper.forCluster();
    }

    @Override
    public void start(Future<Void> start) {
        log.info("Starting SparkOperator for namespace {}", namespace);

        createConfigMapWatch(res -> {
            if (res.succeeded()) {
                configMapWatch = res.result();

                log.info("Setting up periodical reconciliation for namespace {}", namespace);
                this.reconcileTimer = vertx.setPeriodic(this.reconciliationInterval, res2 -> {
                    log.info("Triggering periodic reconciliation for namespace {}...", namespace);
                    reconcileAll("timer");
                });

                log.info("SparkOperator running for namespace {}", namespace);

                // start the HTTP server for healthchecks
                this.startHealthServer();

                start.complete();
            } else {
                log.error("SparkOperator startup failed for namespace {}", namespace, res.cause());
                start.fail("SparkOperator startup failed for namespace " + namespace);
            }
        });
    }

    @Override
    public void stop(Future<Void> stop) {
        log.info("Stopping SparkOperator for namespace {}", namespace);
        vertx.cancelTimer(reconcileTimer);
        configMapWatch.close();
        client.close();

        stop.complete();
    }

    private void createConfigMapWatch(Handler<AsyncResult<Watch>> handler) {
        getVertx().executeBlocking(
                future -> {
                    MixedOperation<ConfigMap, ConfigMapList, DoneableConfigMap, Resource<ConfigMap, DoneableConfigMap>> aux = client.configMaps();
                    Watchable<Watch, Watcher<ConfigMap>> watchable = "*".equals(namespace) ? aux.inAnyNamespace().withLabels(selector) : aux.inNamespace(namespace).withLabels(selector);
                    Watch watch = watchable.watch(new Watcher<ConfigMap>() {
                        @Override
                        public void eventReceived(Action action, ConfigMap cm) {
                            if (ResourceHelper.isCluster(cm)) {
                                log.info("ConfigMap \n{}\n in namespace {} was {}", cm, namespace, action);
                                switch (action) {
                                    case ADDED:
                                        addCluster(cm, isOpenshift);
                                        break;
                                    case DELETED:
                                        deleteCluster(cm, isOpenshift);
                                        break;
                                    case MODIFIED:
                                        break;
                                    case ERROR:
                                        log.error("Failed ConfigMap {} in namespace{} ", cm, namespace);
                                        reconcileAll("watch error");
                                        break;
                                    default:
                                        log.error("Unknown action: {} in namespace {}", action, namespace);
                                        reconcileAll("watch unknown");
                                }
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
                    future.complete(watch);
                }, res -> {
                    if (res.succeeded()) {
                        log.info("ConfigMap watcher running for labels {}", selector);
                        handler.handle(Future.succeededFuture((Watch) res.result()));
                    } else {
                        log.info("ConfigMap watcher failed to start", res.cause());
                        handler.handle(Future.failedFuture("ConfigMap watcher failed to start"));
                    }
                }
        );
    }

    private void addCluster(ConfigMap cm, boolean isOpenshift) {
        String name = ResourceHelper.name(cm);
        log.info("creating cluster:  \n{}\n", name);
        Optional<String> image = HasDataHelper.image(cm);
        Optional<Integer> masters = HasDataHelper.masters(cm).map(m -> Integer.parseInt(m));
        Optional<Integer> workers = HasDataHelper.workers(cm).map(w -> Integer.parseInt(w));
        if (isOpenshift) {
            ProcessRunner pr = new ProcessRunner();
            pr.createCluster(name, image, masters, workers);
        } else {
            KubernetesResourceList list = KubernetesDeployer.getResourceList(name, image, masters, workers);
            client.resourceList(list).createOrReplace();
        }
    }

    private void deleteCluster(ConfigMap cm, boolean isOpenshift) {
        String name = ResourceHelper.name(cm);
        log.info("deleting cluster:  \n{}\n", name);

        if (isOpenshift) {
            ProcessRunner pr = new ProcessRunner();
            pr.deleteCluster(name);
        } else {
            // todo
            log.error("not implemented yet for K8s");
        }
    }

    private void recreateConfigMapWatch() {
        createConfigMapWatch(res -> {
            if (res.succeeded()) {
                log.info("ConfigMap watch recreated in namespace {}", namespace);
                configMapWatch = res.result();
            } else {
                log.error("Failed to recreate ConfigMap watch in namespace {}", namespace);
                // We failed to recreate the Watch. We cannot continue without it. Lets close Vert.x and exit.
                vertx.close();
            }
        });
    }

    /**
     * Periodical reconciliation (in case we lost some event)
     */
    private void reconcileAll(String trigger) {

    }

    /**
     * Start an HTTP health server
     */
    private void startHealthServer() {

        this.vertx.createHttpServer()
                .requestHandler(request -> {

                    if (request.path().equals("/healthy")) {
                        request.response().setStatusCode(200).end();
                    } else if (request.path().equals("/ready")) {
                        request.response().setStatusCode(200).end();
                    }
                })
                .listen(HEALTH_SERVER_PORT);
    }

}
