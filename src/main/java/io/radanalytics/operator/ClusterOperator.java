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
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.radanalytics.operator.AnsiColors.*;

public class ClusterOperator extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(ClusterOperator.class.getName());

    private final KubernetesClient client;
    private final Map<String, String> selector;
    private final String namespace;
    private final boolean isOpenshift;
    private final RunningClusters clusters;
    private volatile Watch configMapWatch;

    public ClusterOperator(String namespace,
                           boolean isOpenshift,
                           KubernetesClient client) {
        this.namespace = namespace;
        this.isOpenshift = isOpenshift;
        this.clusters = new RunningClusters();
        this.client = client;
        this.selector = LabelsHelper.forCluster();
    }

    @Override
    public void start(Future<Void> start) {
        log.info("Starting ClusterOperator for namespace {}", namespace);

        createConfigMapWatch(res -> {
            if (res.succeeded()) {
                configMapWatch = res.result();
                log.info("ClusterOperator running for namespace {}", namespace);

                start.complete();
            } else {
                log.error("ClusterOperator startup failed for namespace {}", namespace, res.cause());
                start.fail("ClusterOperator startup failed for namespace " + namespace);
            }
        });
    }

    @Override
    public void stop(Future<Void> stop) {
        log.info("Stopping ClusterOperator for namespace {}", namespace);
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
                                        modifyCluster(cm, isOpenshift);
                                        break;
                                    case ERROR:
                                        log.error("Failed ConfigMap {} in namespace{} ", cm, namespace);
                                        break;
                                    default:
                                        log.error("Unknown action: {} in namespace {}", action, namespace);
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
        ClusterInfo cluster = ClusterInfo.fromCM(cm);
        if (cluster == null) {
            log.error("something went wrong, unable to parse cluster definition");
        }
        String name = cluster.getName();
        log.info("{}creating{} cluster:  \n{}\n", ANSI_G, ANSI_RESET, name);
        KubernetesResourceList list = KubernetesDeployer.getResourceList(cluster);
        client.resourceList(list).createOrReplace();
        clusters.put(cluster);
        log.info("Cluster {} has been created", name);
    }

    private void deleteCluster(ConfigMap cm, boolean isOpenshift) {
        ClusterInfo cluster = ClusterInfo.fromCM(cm);
        if (cluster == null) {
            log.error("something went wrong, unable to parse cluster definition");
        }
        String name = cluster.getName();
        log.info("{}deleting{} cluster:  \n{}\n", ANSI_G, ANSI_RESET, name);
        client.services().withLabels(KubernetesDeployer.getClusterLabels(name)).delete();
        client.replicationControllers().withLabels(KubernetesDeployer.getClusterLabels(name)).delete();
        client.pods().withLabels(KubernetesDeployer.getClusterLabels(name)).delete();
        clusters.delete(name);
        log.info("Cluster {} has been deleted", name);
    }

    private void modifyCluster(ConfigMap cm, boolean isOpenshift) {
        ClusterInfo newCluster = ClusterInfo.fromCM(cm);
        if (newCluster == null) {
            log.error("something went wrong, unable to parse cluster definition");
        }
        String name = newCluster.getName();
        log.info("modifying cluster:  \n{}\n", name);
        String newImage = newCluster.getCustomImage();
        int newMasters = newCluster.getMasterNodes();
        int newWorkers = newCluster.getWorkerNodes();
        ClusterInfo existingCluster = clusters.getCluster(name);
        if (null == existingCluster) {
            log.error("something went wrong, unable to scale existing cluster. Perhaps it wasn't deployed properly.");
        }

        if (existingCluster.getWorkerNodes() != newWorkers) {
            log.info("{}scaling{} from {}{}{} worker replicas to {}{}{}", ANSI_G, ANSI_RESET, ANSI_Y,
                    existingCluster.getWorkerNodes(), ANSI_RESET, ANSI_Y, newWorkers, ANSI_RESET);
            client.replicationControllers().withName(name + "-w-1").scale(newWorkers);
            clusters.put(newCluster);
            log.info("Cluster {} has been modified", name);
        }
        // todo: image change, masters # change for k8s
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

}