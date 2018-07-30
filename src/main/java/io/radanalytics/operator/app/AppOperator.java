package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Operator(forKind = "app", prefix = "radanalytics.io")
public class AppOperator extends AbstractOperator<AppInfo> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());
    private KubernetesAppDeployer deployer = new KubernetesAppDeployer(entityName, prefix);

    public AppOperator(String namespace,
                       boolean isOpenshift,
                       KubernetesClient client) {
        super(namespace, isOpenshift, client);
    }

    protected void onAdd(AppInfo app, boolean isOpenshift) {
        KubernetesResourceList list = deployer.getResourceList(app, namespace);
        client.resourceList(list).createOrReplace();
    }

    protected void onDelete(AppInfo app, boolean isOpenshift) {
        String name = app.getName();
        client.services().withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.replicationControllers().withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.pods().withLabels(deployer.getLabelsForDeletion(name)).delete();
    }

    protected void onModify(AppInfo newApp, boolean isOpenshift) {
        onDelete(newApp, isOpenshift);
        onAdd(newApp, isOpenshift);
    }

    @Override
    protected boolean isSupported(ConfigMap cm) {
        return ResourceHelper.isAKind(cm, this.entityName, prefix);
    }

    @Override
    protected AppInfo convert(ConfigMap cm) {
        return AppInfo.fromCM(cm);
    }
}
