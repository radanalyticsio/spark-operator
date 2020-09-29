/*
 * Copyright 2018
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

    /**
     * The kind of a ConfigMap:
     * <ul>
     *     <li>{@code radanalytics.io/kind=cluster}
     *         identifies a ConfigMap that is intended to be consumed by
     *         the cluster operator.</li>
     *     <li>{@code radanalytics.io/kind=app}
     *         identifies a ConfigMap that is intended to be consumed
     *         by the app operator.</li>
     *     <li>{@code radanalytics.io/kind=notebook}
     *         identifies a ConfigMap that is intended to be consumed
     *         by the notebook operator.</li>
     * </ul>
     */
    public static final String OPERATOR_KIND_LABEL = "kind";

    public static final String OPERATOR_SEVICE_TYPE_LABEL = "service";
    public static final String OPERATOR_RC_TYPE_LABEL = "rcType";
    public static final String OPERATOR_POD_TYPE_LABEL = "podType";
    public static final String OPERATOR_DEPLOYMENT_LABEL = "deployment";

    public static final Optional<String> getKind(HasMetadata resource, String prefix) {
        return Optional.ofNullable(resource)
                .map(r -> r.getMetadata())
                .map(m -> m.getLabels())
                .map(l -> l.get(prefix + OPERATOR_KIND_LABEL));
    }

    public static Map<String, String> forKind(String kind, String prefix) {
        return Collections.singletonMap(prefix + OPERATOR_KIND_LABEL, kind);
    }
}
