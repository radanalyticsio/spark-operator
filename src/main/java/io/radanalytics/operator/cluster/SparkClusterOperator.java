package io.radanalytics.operator.cluster;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.types.RCSpec;
import io.radanalytics.types.SparkCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.radanalytics.operator.common.AnsiColors.*;

@Operator(forKind = SparkCluster.class, prefix = "radanalytics.io")
public class SparkClusterOperator extends AbstractOperator<SparkCluster> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    private final RunningClusters clusters;
    private KubernetesSparkClusterDeployer deployer;

    public SparkClusterOperator() {
        this.clusters = new RunningClusters();
    }

    protected void onAdd(SparkCluster cluster) {
        KubernetesResourceList list = getDeployer().getResourceList(cluster);
        client.resourceList(list).createOrReplace();
        clusters.put(cluster);
    }

    protected void onDelete(SparkCluster cluster) {
        String name = cluster.getName();
        client.services().withLabels(getDeployer().getDefaultLabels(name)).delete();
        client.replicationControllers().withLabels(getDeployer().getDefaultLabels(name)).delete();
        client.pods().withLabels(getDeployer().getDefaultLabels(name)).delete();
        clusters.delete(name);
    }

    protected void onModify(SparkCluster newCluster) {
        String name = newCluster.getName();
        String newImage = newCluster.getCustomImage();
        int newMasters = Optional.ofNullable(newCluster.getMaster()).orElse(new RCSpec()).getInstances();
        int newWorkers = Optional.ofNullable(newCluster.getWorker()).orElse(new RCSpec()).getInstances();

        SparkCluster existingCluster = clusters.getCluster(name);
        if (null == existingCluster) {
            log.error("something went wrong, unable to scale existing cluster. Perhaps it wasn't deployed properly.");
            return;
        }

        if (existingCluster.getWorker().getInstances() != newWorkers) {
            log.info("{}scaling{} from  {}{}{} worker replicas to  {}{}{}", re(), xx(), ye(),
                    existingCluster.getWorker().getInstances(), xx(), ye(), newWorkers, xx());
            client.replicationControllers().withName(name + "-w").scale(newWorkers);
            clusters.put(newCluster);
        }
        // todo: image change, masters # change for k8s
    }

    public KubernetesSparkClusterDeployer getDeployer() {
        if (this.deployer == null) {
            this.deployer = new KubernetesSparkClusterDeployer(client, entityName, prefix);
        }
        return deployer;
    }
}
