package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.types.SparkApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Operator(forKind = SparkApplication.class, prefix = "radanalytics.io")
public class AppOperator extends AbstractOperator<SparkApplication> {

    @Inject
    private Logger log;
    private KubernetesAppDeployer deployer;
    private Map<String, SparkApplication> apps;

    public AppOperator(){
        this.apps = new HashMap<>();
    }

    private void put(SparkApplication app) {
        apps.put(app.getName(), app);
    }

    private void delete(String name) {
        if (apps.containsKey(name)) {
            apps.remove(name);
        }
    }

    private SparkApplication getApp(String name) {
        return this.apps.get(name);
    }

    private void updateStatus(SparkApplication app, String state) {
	    for (int i=0; i<3; i++) {
            try {
	            setCRStatus(state, app.getNamespace(), app.getName() );
		        break;
	        }
	        catch(Exception e) {
                try {Thread.sleep(500);} catch(Exception t) {}
	        }
	    }
    }

    @Override
    protected void onInit() {
        this.deployer = new KubernetesAppDeployer(entityName, prefix);
    }

    @Override
    protected void onAdd(SparkApplication app) {
        KubernetesResourceList list = deployer.getResourceList(app, namespace);
        client.resourceList(list).inNamespace(namespace).createOrReplace();
        updateStatus(app, "ready" );
        put(app);
    }

    @Override
    protected void onModify(SparkApplication newApp) {

        // TODO This comparison works to rule out a change in status because
        // we added the status block in the AbstractOperator universally,
        // ie it is not actually included in the SparkApplication type
        // definition generated from json. If that ever changes, then
        // this comparison will have to be a little smarter.
        SparkApplication existingApp = getApp(newApp.getName());
        if (null == existingApp || !newApp.equals(existingApp)) {
            super.onModify(newApp);
        }
    }

    @Override
    protected void onDelete(SparkApplication app) {
        String name = app.getName();
        updateStatus(app, "deleted");
        delete(name);
        client.services().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.replicationControllers().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
        client.pods().inNamespace(namespace).withLabels(deployer.getLabelsForDeletion(name)).delete();
    }
}
