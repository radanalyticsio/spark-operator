package io.radanalytics.operator.cluster;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.radanalytics.types.RCSpec;
import io.radanalytics.types.SparkCluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class RunningClusters {

    public static final Gauge runningClusters = Gauge.build()
            .name("operator_running_clusters")
            .help("Spark clusters that are currently running.")
            .register();

    public static final Gauge workers = Gauge.build()
            .name("operator_running_workers")
            .help("Number of workers per cluster name.")
            .labelNames("cluster")
            .register();

    public static final Gauge startedTotal = Gauge.build()
            .name("operator_started_total")
            .help("Spark clusters has been started by operator.")
            .register();

    private final Map<String, SparkCluster> clusters;

    public RunningClusters() {
        clusters = new HashMap<>();
        runningClusters.set(0);
    }

    public void put(SparkCluster ci) {
        runningClusters.inc();
        startedTotal.inc();
        workers.labels(ci.getName()).set(Optional.ofNullable(ci.getWorker()).orElse(new RCSpec()).getInstances());
        clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        if (clusters.containsKey(name)) {
            runningClusters.dec();
            workers.labels(name).set(0);
            clusters.remove(name);
        }
    }

    public SparkCluster getCluster(String name) {
        return this.clusters.get(name);
    }

    public void resetMetrics() {
        startedTotal.set(0);
        workers.clear();
        startedTotal.set(0);
    }

}
