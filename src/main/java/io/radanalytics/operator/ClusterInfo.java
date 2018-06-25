package io.radanalytics.operator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.resource.HasDataHelper;
import io.radanalytics.operator.resource.ResourceHelper;

import java.util.Optional;

import static io.radanalytics.operator.OperatorConfig.DEFAULT_SPARK_IMAGE;

public class ClusterInfo {
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

    public static ClusterInfo fromCM(ConfigMap cm) {
        String name = ResourceHelper.name(cm);
        Optional<String> maybeImage = HasDataHelper.image(cm);
        Optional<Integer> maybeMasters = HasDataHelper.masters(cm).map(m -> Integer.parseInt(m));
        Optional<Integer> maybeWorkers = HasDataHelper.workers(cm).map(w -> Integer.parseInt(w));
        return new ClusterInfo(name, maybeImage, maybeMasters, maybeWorkers);
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
