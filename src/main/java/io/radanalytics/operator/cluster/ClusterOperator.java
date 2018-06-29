package io.radanalytics.operator.cluster;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.KubernetesDeployer;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.radanalytics.operator.common.AnsiColors.*;

public class ClusterOperator extends AbstractOperator<ClusterInfo> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    private final RunningClusters clusters;

    public ClusterOperator(String namespace,
                           boolean isOpenshift,
                           KubernetesClient client) {
        super(namespace, isOpenshift, client, "Cluster", LabelsHelper.forCluster());
        this.clusters = new RunningClusters();
    }

    protected void onAdd(ClusterInfo cluster, boolean isOpenshift) {
        KubernetesResourceList list = KubernetesDeployer.getResourceList(cluster);
        client.resourceList(list).createOrReplace();
        clusters.put(cluster);
    }

    protected void onDelete(ClusterInfo cluster, boolean isOpenshift) {
        String name = cluster.getName();
        client.services().withLabels(KubernetesDeployer.getDefaultLabels(name)).delete();
        client.replicationControllers().withLabels(KubernetesDeployer.getDefaultLabels(name)).delete();
        client.pods().withLabels(KubernetesDeployer.getDefaultLabels(name)).delete();
        clusters.delete(name);
    }

    protected void onModify(ClusterInfo newCluster, boolean isOpenshift) {
        String name = newCluster.getName();
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
            client.replicationControllers().withName(name + "-w").scale(newWorkers);
            clusters.put(newCluster);
        }
        // todo: image change, masters # change for k8s
    }

    @Override
    protected boolean isSupported(ConfigMap cm) {
        return ResourceHelper.isCluster(cm);
    }

    @Override
    protected ClusterInfo convert(ConfigMap cm) {
        return ClusterInfo.fromCM(cm);
    }
}
