package io.radanalytics.operator.cluster;

import io.radanalytics.types.ClusterInfo;

import java.util.HashMap;
import java.util.Map;


public class RunningClusters {

    private final Map<String, ClusterInfo> clusters = new HashMap<>();

    public void put(ClusterInfo ci) {
        this.clusters.put(ci.getName(), ci);
    }

    public void delete(String name) {
        this.clusters.remove(name);
    }

    public ClusterInfo getCluster(String name) {
        return this.clusters.get(name);
    }

}
