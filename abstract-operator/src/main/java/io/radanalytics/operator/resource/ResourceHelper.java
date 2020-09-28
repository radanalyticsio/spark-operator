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
     * Returns the value of the {@code metadata.name} of the given {@code cm}.
     *
     * @param cm config map object
     * @return the name
     */
    public static String name(ConfigMap cm) {
        return cm.getMetadata().getName();
    }

    public static boolean isAKind(ConfigMap cm, String kind, String prefix) {
        return LabelsHelper.getKind(cm, prefix).map(k -> kind.equals(k)).orElse(false);
    }
}
