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

    public static final String OPERATOR_DOMAIN = "radanalytics.io/";

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
    public static final String OPERATOR_KIND_LABEL = OPERATOR_DOMAIN + "kind";

    // the values
    public static final String OPERATOR_KIND_CLUSTER_LABEL = "cluster";
    public static final String OPERATOR_KIND_NOTEBOOK_LABEL = "notebook";
    public static final String OPERATOR_KIND_APP_LABEL = "app";

    public static final String OPERATOR_SEVICE_TYPE_LABEL = OPERATOR_DOMAIN + "service";
    //the values
    public static final String OPERATOR_TYPE_UI_LABEL = "ui";

    public static final String OPERATOR_RC_TYPE_LABEL = OPERATOR_DOMAIN + "rcType";
    public static final String OPERATOR_POD_TYPE_LABEL = OPERATOR_DOMAIN + "podType";
    public static final String OPERATOR_DEPLOYMENT_LABEL = OPERATOR_DOMAIN + "deployment";
    //the values
    public static final String OPERATOR_TYPE_MASTER_LABEL = "master";
    public static final String OPERATOR_TYPE_WORKER_LABEL = "worker";

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

    public static Map<String, String> forNotebook() {
        return forKind(OPERATOR_KIND_NOTEBOOK_LABEL);
    }

    private static Map<String, String> forKind(String kind) {
        return Collections.singletonMap(OPERATOR_KIND_LABEL, kind);
    }
}
