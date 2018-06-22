package io.radanalytics.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.radanalytics.operator.OperatorConfig.DEFAULT_SPARK_IMAGE;

public class RunningClusters {
    public static class ClusterInfo {
        private final String name;
        private final String image;
        private final int masters;
        private final int workers;


        public ClusterInfo(String name, Optional<String> maybeImage, Optional<Integer> maybeMasters, Optional<Integer> maybeWorkers) {
            this.name = name;
            this.image = maybeImage.orElse(DEFAULT_SPARK_IMAGE);
            this.masters = maybeMasters.orElse(1);
            this.workers = maybeWorkers.orElse(1);
        }

        public String getName() {
            return name;
        }

        public String getImage() {
            return image;
        }

        public int getMasters() {
            return masters;
        }

        public int getWorkers() {
            return workers;
        }

        @Override
        public String toString() {
            return "ClusterInfo{" +
                    "name='" + name + '\'' +
                    ", image='" + image + '\'' +
                    ", masters=" + masters +
                    ", workers=" + workers +
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

    private final Map<String, ClusterInfo> clusters = new HashMap<>();

    public void put(String name, Optional<String> maybeImage, Optional<Integer> maybeMasters, Optional<Integer> maybeWorkers) {
        this.clusters.put(name, new ClusterInfo(name, maybeImage, maybeMasters, maybeWorkers));
    }

    public void delete(String name) {
        this.clusters.remove(name);
    }

    public ClusterInfo getCluster(String name) {
        return this.clusters.get(name);
    }

}
