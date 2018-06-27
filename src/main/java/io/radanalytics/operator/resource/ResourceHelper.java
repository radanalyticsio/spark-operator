/*
 * Copyright 2018
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.ConfigMap;

import java.util.Optional;

/**
 * A helper for parsing the top-lvl section inside the K8s resource
 */
public class ResourceHelper {

    /**
     * Returns the value of the {@code metadata.name} of the given {@code resource}.
     */
    public static String name(ConfigMap cm) {
        return cm.getMetadata().getName();
    }

    public static boolean isCluster(ConfigMap cm) {
        return isAKind(cm, LabelsHelper.OPERATOR_KIND_CLUSTER_LABEL);
    }

    public static boolean isApp(ConfigMap cm) {
        return isAKind(cm, LabelsHelper.OPERATOR_KIND_APP_LABEL);
    }

    private static boolean isAKind(ConfigMap cm, String kind) {
        return LabelsHelper.getKind(cm).map(k -> kind.equals(k)).orElse(false);
    }
}
