package io.radanalytics.operator.historyServer;

import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.types.SparkHistoryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operator(forKind = SparkHistoryServer.class, prefix = "radanalytics.io")
public class HistoryServerOperator extends AbstractOperator<SparkHistoryServer> {

    private static final Logger log = LoggerFactory.getLogger(HistoryServerOperator.class.getName());
    private KubernetesHistoryServerDeployer deployer;

    @Override
    protected void onInit() {
        this.deployer = new KubernetesHistoryServerDeployer(entityName, prefix);
    }

    @Override
    protected void onAdd(SparkHistoryServer server) {
        log.info("Spark history server added");

        // todo: create deployment, start the history server, put all the config into env variable, expose service if necessary
//        KubernetesResourceList list = deployer.getResourceList(server, namespace);
//        client.resourceList(list).inNamespace(namespace).createOrReplace();
    }

    @Override
    protected void onDelete(SparkHistoryServer server) {
        log.info("Spark history server removed");
        String name = server.getName();
        client.services().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.replicationControllers().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.pods().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
    }
}
