package io.radanalytics.operator.cluster;

import io.radanalytics.types.SparkCluster;
import io.radanalytics.types.Worker;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class RunningClusters {

    private final Map<String, SparkCluster> clusters;
    private final String namespace;

    @Inject
    private MetricsHelper metrics;

    public RunningClusters(String namespace) {
        clusters = new HashMap<>();
        this.namespace = namespace;
        metrics.runningClusters.labels(namespace).set(0);
    }

    public void put(SparkCluster ci) {
        metrics.runningClusters.labels(namespace).inc();
        metrics.startedTotal.labels(namespace).inc();
        metrics.workers.labels(ci.getName(), namespace).set(Optional.ofNullable(ci.getWorker()).orElse(new Worker()).getInstances());
        clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        if (clusters.containsKey(name)) {
            metrics.runningClusters.labels(namespace).dec();
            metrics.workers.labels(name, namespace).set(0);
            clusters.remove(name);
        }
    }

    public SparkCluster getCluster(String name) {
        return this.clusters.get(name);
    }

    public void resetMetrics() {
        metrics.startedTotal.labels(namespace).set(0);
        clusters.forEach((c, foo) -> metrics.workers.labels(c, namespace).set(0));
        metrics.startedTotal.labels(namespace).set(0);
    }

}
