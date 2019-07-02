package io.radanalytics.operator.cluster;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

import javax.inject.Singleton;

@Singleton
public class MetricsHelper {
    private static final String PREFIX = "operator_";

    public final Counter reconciliationsTotal = Counter.build()
            .name(PREFIX + "full_reconciliations_total")
            .help("How many times the full reconciliation has been run.")
            .labelNames("ns")
            .register();

    public final Gauge runningClusters = Gauge.build()
            .name(PREFIX + "running_clusters")
            .help("Spark clusters that are currently running.")
            .labelNames("ns")
            .register();

    public final Gauge workers = Gauge.build()
            .name(PREFIX + "running_workers")
            .help("Number of workers per cluster name.")
            .labelNames("cluster", "ns")
            .register();

    public final Gauge startedTotal = Gauge.build()
            .name(PREFIX + "started_clusters_total")
            .help("Spark clusters has been started by operator.")
            .labelNames("ns")
            .register();
}
