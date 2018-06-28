package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.resource.HasDataHelper;

public class AppInfo implements EntityInfo {
    private String name;
    private String image;
    private String mainApplicationFile;
    private String mainClass;

    public AppInfo() {
        // has to be there because of the snakeyaml library
    }

    public static AppInfo fromCM(ConfigMap cm) {
        return HasDataHelper.parseCM(AppInfo.class, cm);
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getMainApplicationFile() {
        return mainApplicationFile;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setMainApplicationFile(String mainApplicationFile) {
        this.mainApplicationFile = mainApplicationFile;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AppInfo that = (AppInfo) o;

        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
