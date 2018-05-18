/*
 * Copyright 2018, OSHINKO authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.*;

/**
 * An immutable set of labels
 */
public class Labels {

    public static final String OSHINKO_DOMAIN = "radanalytics.io/";

    /**
     * The kind of a ConfigMap:
     * <ul>
     *     <li>{@code radanalytics.io/kind=cluster}
     *         identifies a ConfigMap that is intended to be consumed by
     *         the cluster operator.</li>
     *     <li>{@code radanalytics.io/kind=topic}
     *         identifies a ConfigMap that is intended to be consumed
     *         by the topic operator.</li>
     * </ul>
     */
    public static final String OSHINKO_KIND_LABEL = OSHINKO_DOMAIN + "kind";
    /**
     * The type of OSHINKO assembly.
//     * @see AssemblyType
     */
    public static final String OSHINKO_TYPE_LABEL = OSHINKO_DOMAIN + "type";

    /**
     * The OSHINKO cluster the resource is part of.
     * The value is the cluster name (i.e. the name of the cluster CM)
     */
    public static final String OSHINKO_CLUSTER_LABEL = OSHINKO_DOMAIN + "cluster";

    /**
     * The name of the K8S resource.
     * This is often the same name as the cluster
     * (i.e. the same as {@code radanalytics.io/cluster})
     * but is different in some cases (e.g. headful and headless services)
     */
    public static final String OSHINKO_NAME_LABEL = OSHINKO_DOMAIN + "name";

    /**
     * The empty set of labels.
     */
    public static final Labels EMPTY = new Labels(emptyMap());

    private final Map<String, String> labels;

    /**
     * Returns the value of the {@code radanalytics.io/cluster} label of the given {@code resource}.
     */
    public static String cluster(HasMetadata resource) {
        return resource.getMetadata().getLabels().get(Labels.OSHINKO_CLUSTER_LABEL);
    }

//    /**
//     * Returns the value of the {@code OSHINKO.io/type} label of the given {@code resource}.
//     */
//    public static AssemblyType type(HasMetadata resource) {
//        String type = resource.getMetadata().getLabels().get(Labels.OSHINKO_TYPE_LABEL);
//        return type != null ? AssemblyType.fromName(type) : null;
//    }

    /**
     * Returns the value of the {@code radanalytics.io/name} label of the given {@code resource}.
     */
    public static String name(HasMetadata resource) {
        return resource.getMetadata().getLabels().get(Labels.OSHINKO_NAME_LABEL);
    }

    /**
     * Returns the value of the {@code radanalytics.io/kind} label of the given {@code resource}.
     */
    public static String kind(HasMetadata resource) {
        return resource.getMetadata().getLabels().get(Labels.OSHINKO_KIND_LABEL);
    }

    public static Labels userLabels(Map<String, String> userLabels) {
        for (String key : userLabels.keySet()) {
            if (key.startsWith(OSHINKO_DOMAIN)
                    && !key.equals(OSHINKO_KIND_LABEL)) {
                throw new IllegalArgumentException("User labels includes a OSHINKO label that is not "
                        + OSHINKO_KIND_LABEL + ": " + key);
            }
        }
        return new Labels(userLabels);
    }

    /**
     * Returns the labels of the given {@code resource}.
     */
    public static Labels fromResource(HasMetadata resource) {
        return new Labels(resource.getMetadata().getLabels());
    }

    private Labels(Map<String, String> labels) {
        this.labels = unmodifiableMap(new HashMap(labels));
    }

    private Labels with(String label, String value) {
        Map<String, String> newLabels = new HashMap<>(labels.size() + 1);
        newLabels.putAll(labels);
        newLabels.put(label, value);
        return new Labels(newLabels);
    }

    private Labels without(String label) {
        Map<String, String> newLabels = new HashMap<>(labels);
        newLabels.remove(label);
        return new Labels(newLabels);
    }

//    /**
//     * The same labels as this instance, but with the given {@code type} for the {@code radanalytics.io/type} key.
//     */
//    public Labels withType(AssemblyType type) {
//        return with(OSHINKO_TYPE_LABEL, type.toString());
//    }

    /**
     * The same labels as this instance, but without any {@code radanalytics.io/type} key.
     */
    public Labels withoutType() {
        return without(OSHINKO_TYPE_LABEL);
    }

    /**
     * The same labels as this instance, but with the given {@code kind} for the {@code radanalytics.io/kind} key.
     */
    public Labels withKind(String kind) {
        return with(OSHINKO_KIND_LABEL, kind);
    }

    /**
     * The same labels as this instance, but without any {@code radanalytics.io/kind} key.
     */
    public Labels withoutKind() {
        return without(OSHINKO_KIND_LABEL);
    }

    /**
     * The same labels as this instance, but with the given {@code cluster} for the {@code radanalytics.io/cluster} key.
     */
    public Labels withCluster(String cluster) {
        return with(OSHINKO_CLUSTER_LABEL, cluster);
    }

    /**
     * The same labels as this instance, but with the given {@code name} for the {@code OSHINKO.io/name} key.
     */
    public Labels withName(String name) {
        return with(OSHINKO_NAME_LABEL, name);
    }

    /**
     * An unmodifiable map of the labels.
     */
    public Map<String, String> toMap() {
        return labels;
    }

    /**
     * A singleton instance with the given {@code cluster} for the {@code radanalytics.io/cluster} key.
     */
    public static Labels forCluster(String cluster) {
        return new Labels(singletonMap(OSHINKO_CLUSTER_LABEL, cluster));
    }

    /**
     * A singleton instance with the given {@code type} for the {@code radanalytics.io/type} key.
     */
//    public static Labels forType(AssemblyType type) {
//        return new Labels(singletonMap(OSHINKO_TYPE_LABEL, type.toString()));
//    }

    /**
     * A singleton instance with the given {@code kind} for the {@code OSHINKO.io/kind} key.
     */
    public static Labels forKind(String kind) {
        return new Labels(singletonMap(OSHINKO_KIND_LABEL, kind));
    }

    /**
     * Return the value of the {@code radanalytics.io/type}.
     */
//    public AssemblyType type() {
//        String type = labels.get(OSHINKO_TYPE_LABEL);
//        return type != null ? AssemblyType.fromName(type) : null;
//    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Labels labels1 = (Labels) o;
        return Objects.equals(labels, labels1.labels);
    }

    @Override
    public int hashCode() {

        return Objects.hash(labels);
    }

    @Override
    public String toString() {
        return "Labels" + labels;
    }
}
