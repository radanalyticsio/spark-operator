package io.radanalytics.operator.cluster;

import io.radanalytics.types.SparkCluster;
import io.radanalytics.types.Worker;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.radanalytics.operator.cluster.MetricsHelper.*;



public class RunningClusters {

    private final Map<String, SparkCluster> clusters;
    private final String namespace;

    public RunningClusters(String namespace) {
        clusters = new HashMap<>();
        this.namespace = namespace;
        runningClusters.labels(namespace).set(0);
    }

    public void put(SparkCluster ci) {
        runningClusters.labels(namespace).inc();
        startedTotal.labels(namespace).inc();
        workers.labels(ci.getName(), namespace).set(Optional.ofNullable(ci.getWorker()).orElse(new Worker()).getInstances());
        clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        if (clusters.containsKey(name)) {
            runningClusters.labels(namespace).dec();
            workers.labels(name, namespace).set(0);
            clusters.remove(name);
        }
    }

    public SparkCluster getCluster(String name) {
        return this.clusters.get(name);
    }

    public void resetMetrics() {
        startedTotal.labels(namespace).set(0);
        clusters.forEach((c, foo) -> workers.labels(c, namespace).set(0));
        startedTotal.labels(namespace).set(0);
    }

}
