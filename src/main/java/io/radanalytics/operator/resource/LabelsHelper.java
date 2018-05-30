/*
 * Copyright 2018, OSHINKO authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * A helper for parsing the {@code metadata.labels} section inside the K8s resource
 */
public class LabelsHelper {

    public static final String OPERATOR_DOMAIN = "radanalytics.io/";

    /**
     * The kind of a ConfigMap:
     * <ul>
     *     <li>{@code radanalytics.io/kind=spark-cluster}
     *         identifies a ConfigMap that is intended to be consumed by
     *         the cluster operator.</li>
     *     <li>{@code radanalytics.io/kind=app}
     *         identifies a ConfigMap that is intended to be consumed
     *         by the app operator.</li>
     * </ul>
     */
    public static final String OPERATOR_KIND_LABEL = OPERATOR_DOMAIN + "kind";

    public static final String OPERATOR_KIND_CLUSTER_LABEL = "cluster";

    public static final String OPERATOR_KIND_APP_LABEL = "app";

    public static final Optional<String> getKind(HasMetadata resource) {
        return Optional.ofNullable(resource)
                .map(r -> r.getMetadata())
                .map(m -> m.getLabels())
                .map(l -> l.get(OPERATOR_KIND_LABEL));
    }

    public static Map<String, String> forCluster() {
        return forKind(OPERATOR_KIND_CLUSTER_LABEL);
    }

    public static Map<String, String> forApp() {
        return forKind(OPERATOR_KIND_APP_LABEL);
    }

    private static Map<String, String> forKind(String kind) {
        return Collections.singletonMap(OPERATOR_KIND_LABEL, kind);
    }
}
