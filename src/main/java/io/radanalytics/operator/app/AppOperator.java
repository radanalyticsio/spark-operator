package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.types.SparkApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operator(forKind = SparkApplication.class, prefix = "radanalytics.io")
public class AppOperator extends AbstractOperator<SparkApplication> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());
    private KubernetesAppDeployer deployer;

    @Override
    protected void onInit() {
        this.deployer = new KubernetesAppDeployer(entityName, prefix);
    }

    @Override
    protected void onAdd(SparkApplication app) {
        KubernetesResourceList list = deployer.getResourceList(app, namespace);
        client.resourceList(list).inNamespace(namespace).createOrReplace();
    }

    @Override
    protected void onDelete(SparkApplication app) {
        String name = app.getName();
        client.services().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.replicationControllers().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.pods().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
    }
}
