package io.radanalytics.operator.cluster;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.resource.HasDataHelper;

import java.util.Collections;
import java.util.List;

import static io.radanalytics.operator.common.OperatorConfig.DEFAULT_SPARK_IMAGE;

public class ClusterInfo implements EntityInfo {
    public static class DL {
        private String url;
        private String to;

        public DL() {
            // has to be there because of the snakeyaml library
        }

        public String getUrl() {
            return url;
        }

        public String getTo() {
            return to;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }

    private String name;
    private String customImage;
    private int masterNodes;
    private int workerNodes;
    private List<DL> downloadData;

    public ClusterInfo() {
        // has to be there because of the snakeyaml library
    }

    public static ClusterInfo fromCM(ConfigMap cm) {
        return HasDataHelper.parseCM(ClusterInfo.class, cm);
    }

    public String getName() {
        return name;
    }

    public String getCustomImage() {
        return customImage != null ? customImage : DEFAULT_SPARK_IMAGE;
    }

    public int getMasterNodes() {
        return masterNodes <= 0 ? 1 : masterNodes;
    }

    public int getWorkerNodes() {
        return workerNodes <= 0 ? 1 : workerNodes;
    }

    public List<DL> getDownloadData() {
        return downloadData == null ? Collections.emptyList() : downloadData;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCustomImage(String customImage) {
        this.customImage = customImage;
    }

    public void setMasterNodes(int masterNodes) {
        this.masterNodes = masterNodes;
    }

    public void setWorkerNodes(int workerNodes) {
        this.workerNodes = workerNodes;
    }

    public void setDownloadData(List<DL> downloadData) {
        this.downloadData = downloadData;
    }

    @Override
    public String toString() {
        return "ClusterInfo{" +
                "name='" + getName() + '\'' +
                ", customImage='" + getCustomImage() + '\'' +
                ", masterNodes=" + getMasterNodes() +
                ", workerNodes=" + getWorkerNodes() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClusterInfo that = (ClusterInfo) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
