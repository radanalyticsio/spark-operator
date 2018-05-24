/*
 * Copyright 2018, OSHINKO authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.ConfigMap;

import java.util.Optional;

/**
 * A helper for parsing the data section inside the K8s resource
 */
public class HasDataHelper {

    /**
     * Returns the value of the {@code data.worker-nodes} of the given {@code resource}.
     */
    public static Optional<String> workers(ConfigMap cm) {
        return getValue(cm, "worker-nodes");
    }

    /**
     * Returns the value of the {@code data.master-nodes} of the given {@code resource}.
     */
    public static Optional<String> masters(ConfigMap cm) {
        return getValue(cm, "master-nodes");
    }

    /**
     * Returns the value of the {@code data.custom-image} of the given {@code resource}.
     */
    public static Optional<String> image(ConfigMap cm) {
        return getValue(cm, "custom-image");
    }

    private static Optional<String> getValue(ConfigMap cm, String key) {
        return Optional.ofNullable(cm.getData().get(key));
    }
}
