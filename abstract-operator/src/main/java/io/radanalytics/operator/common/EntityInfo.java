package io.radanalytics.operator.common;

import java.util.Objects;

/**
 * Simple abstract class that captures the information about the object we are interested in in the Kubernetes cluster.
 * Field called 'name' is the only compulsory information and it represents the name of the configmap.
 *
 * By extending this class and adding new fields to it, you can create a rich configuration object. The structure
 * of this object will be expected in the watched config maps and there are also some prepared method for YAML -&gt;
 * 'T extends EntityInfo' conversions prepared in
 * {@link io.radanalytics.operator.resource.HasDataHelper#parseCM(Class, io.fabric8.kubernetes.api.model.ConfigMap)}.
 */
public abstract class EntityInfo {
    protected String name;
    protected String namespace;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityInfo that = (EntityInfo) o;
        return Objects.equals(name, that.name) && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, namespace);
    }
}
