package io.radanalytics.operator.cluster;

import io.radanalytics.types.SparkCluster;

import java.util.HashMap;
import java.util.Map;


public class RunningClusters {

    private final Map<String, SparkCluster> clusters = new HashMap<>();

    public void put(SparkCluster ci) {
        this.clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        this.clusters.remove(name);
    }

    public SparkCluster getCluster(String name) {
        return this.clusters.get(name);
    }

}
