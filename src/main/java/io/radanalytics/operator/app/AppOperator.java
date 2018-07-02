package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.operator.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppOperator extends AbstractOperator<AppInfo> {

    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());


    public AppOperator(String namespace,
                       boolean isOpenshift,
                       KubernetesClient client) {
        super(namespace, isOpenshift, client, "App", LabelsHelper.forApp());
    }

    protected void onAdd(AppInfo app, boolean isOpenshift) {
        KubernetesResourceList list = KubernetesAppDeployer.getResourceList(app, namespace);
        client.resourceList(list).createOrReplace();
    }

    protected void onDelete(AppInfo app, boolean isOpenshift) {
        String name = app.getName();
        client.services().withLabels(KubernetesAppDeployer.getLabelsForDeletion(name)).delete();
        client.replicationControllers().withLabels(KubernetesAppDeployer.getLabelsForDeletion(name)).delete();
        client.pods().withLabels(KubernetesAppDeployer.getLabelsForDeletion(name)).delete();
    }

    protected void onModify(AppInfo newApp, boolean isOpenshift) {
        onDelete(newApp, isOpenshift);
        onAdd(newApp, isOpenshift);
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
