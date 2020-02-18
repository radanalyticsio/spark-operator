package io.radanalytics.operator.historyServer;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.Operator;
import io.radanalytics.types.SparkHistoryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.lang.Thread;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.WeakHashMap;

@Singleton
@Operator(forKind = SparkHistoryServer.class, prefix = "radanalytics.io")
public class HistoryServerOperator extends AbstractOperator<SparkHistoryServer> {

    @Inject
    private Logger log;
    private KubernetesHistoryServerDeployer deployer;
    private boolean osClient = false;
    private Map<String, KubernetesResourceList> cache = new WeakHashMap<>();
    private Map<String, SparkHistoryServer> hss;

    public HistoryServerOperator() {
        this.hss = new HashMap<>();
    }

    private void put(SparkHistoryServer hs) {
        hss.put(hs.getName(), hs);
    }

    private void delete(String name) {
        if (hss.containsKey(name)) {
            hss.remove(name);
        }
    }

    private SparkHistoryServer getHS(String name) {
        return this.hss.get(name);
    }

   private void updateStatus(SparkHistoryServer hs, String state) {
        for (int i=0; i<3; i++) {
            try {
	            setCRStatus(state, hs.getNamespace(), hs.getName() );
		        break;
	        }
	        catch(Exception e) {
	            log.warn("failed to update status {} for {} in {}", state, hs.getName(), hs.getNamespace());
                try {Thread.sleep(500);} catch(Exception t) {}
	        }
	    }
    }

    @Override
    protected void onInit() {
        this.deployer = new KubernetesHistoryServerDeployer(entityName, prefix);
    }

    @Override
    protected void onAdd(SparkHistoryServer hs) {
        log.info("Spark history server added");

        KubernetesResourceList list = deployer.getResourceList(hs, namespace, isOpenshift);
        if (isOpenshift && hs.getExpose() && !osClient) {

            // we will create openshift specific resource (Route)
            this.client = new DefaultOpenShiftClient();
            osClient = true;
        }
        client.resourceList(list).inNamespace(namespace).createOrReplace();
        cache.put(hs.getName(), list);
        updateStatus(hs, "ready");
        put(hs);
    }

    @Override
    protected void onModify(SparkHistoryServer newHs) {

        // TODO This comparison works to rule out a change in status because
        // we added the status block in the AbstractOperator universally,
        // ie it is not actually included in the SparkHistoryServer type
        // definition generated from json. If that ever changes, then
        // this comparison will have to be a little smarter.
        SparkHistoryServer existingHs = getHS(newHs.getName());
        if (null == existingHs || !newHs.equals(existingHs)) {
            super.onModify(newHs);
        }
    }

    @Override
    protected void onDelete(SparkHistoryServer hs) {
        log.info("Spark history server removed");
        String name = hs.getName();
        updateStatus(hs, "deleted");
        delete(name);
        KubernetesResourceList list = Optional.ofNullable(cache.get(name)).orElse(deployer.getResourceList(hs, namespace, isOpenshift));
        client.resourceList(list).inNamespace(namespace).delete();
        cache.remove(name);
    }
}
