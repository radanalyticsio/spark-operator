package io.radanalytics.operator.historyServer;

import io.radanalytics.types.HistoryServer;
import io.radanalytics.types.SparkCluster;
import io.radanalytics.types.SparkHistoryServer;

public class HistoryServerHelper {

    public static boolean needsVolume(SparkHistoryServer hs) {
        return HistoryServer.Type.sharedVolume.equals(hs.getType());
    }

    public static boolean needsVolume(SparkCluster cluster) {
        return null != cluster.getHistoryServer() && HistoryServer.Type.sharedVolume.equals(cluster.getHistoryServer().getType());
    }
}
