package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppOperator extends AbstractOperator<AppInfo> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

//    private final RunningClusters apps;

    public AppOperator(String namespace,
                       boolean isOpenshift,
                       KubernetesClient client) {
        super(namespace, isOpenshift, client, "App", LabelsHelper.forApp());
//        this.apps = new RunningClusters();
    }

    protected void onAdd(AppInfo app, boolean isOpenshift) {
        String name = app.getName();
//        KubernetesResourceList list = KubernetesSparkClusterDeployer.getResourceList(cluster);
//        client.resourceList(list).createOrReplace();
//        apps.put(app);
    }

    protected void onDelete(AppInfo app, boolean isOpenshift) {
//        ClusterInfo cluster = ClusterInfo.fromCM(cm);
//        String name = cluster.getName();
//        client.services().withLabels(KubernetesSparkClusterDeployer.getDefaultLabels(name)).delete();
//        client.replicationControllers().withLabels(KubernetesSparkClusterDeployer.getDefaultLabels(name)).delete();
//        client.pods().withLabels(KubernetesSparkClusterDeployer.getDefaultLabels(name)).delete();
//        clusters.delete(name);
    }

    protected void onModify(AppInfo newApp, boolean isOpenshift) {
//        ClusterInfo newCluster = ClusterInfo.fromCM(cm);
//        String name = newCluster.getName();
//        String newImage = newCluster.getCustomImage();
//        int newMasters = newCluster.getMasterNodes();
//        int newWorkers = newCluster.getWorkerNodes();
//        ClusterInfo existingCluster = clusters.getCluster(name);
//        if (null == existingCluster) {
//            log.error("something went wrong, unable to scale existing cluster. Perhaps it wasn't deployed properly.");
//        }
//
//        if (existingCluster.getWorkerNodes() != newWorkers) {
//            log.info("{}scaling{} from {}{}{} worker replicas to {}{}{}", ANSI_G, ANSI_RESET, ANSI_Y,
//                    existingCluster.getWorkerNodes(), ANSI_RESET, ANSI_Y, newWorkers, ANSI_RESET);
//            client.replicationControllers().withName(name + "-w").scale(newWorkers);
//            clusters.put(newCluster);
//        }
//        // todo: image change, masters # change for k8s
    }

    @Override
    protected boolean isSupported(ConfigMap cm) {
        return ResourceHelper.isApp(cm);
    }

    @Override
    protected AppInfo convert(ConfigMap cm) {
        return AppInfo.fromCM(cm);
    }
}
