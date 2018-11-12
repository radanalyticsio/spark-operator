package io.radanalytics.operator.cluster;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.radanalytics.types.SparkCluster;

import java.util.HashMap;
import java.util.Map;


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

    public static final Counter startedTotal = Counter.build()
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
        workers.labels(ci.getName()).set(ci.getWorker().getInstances());
        clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        runningClusters.dec();
        workers.labels(name).set(0);
        clusters.remove(name);
    }

    public SparkCluster getCluster(String name) {
        return this.clusters.get(name);
    }

}
