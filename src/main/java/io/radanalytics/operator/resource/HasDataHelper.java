/*
 * Copyright 2018
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.cluster.ClusterInfo;
import io.radanalytics.operator.common.EntityInfo;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.Optional;

/**
 * A helper for parsing the data section inside the K8s resource
 */
public class HasDataHelper {

    public static <T extends EntityInfo> T parseYaml(Class<T> clazz, String yamlDoc, String name) {
        Yaml snake = new Yaml(new Constructor(clazz));
        T cluster = snake.load(yamlDoc);
        cluster.setName(name);
        return cluster;
    }

    public static <T extends EntityInfo> T parseCM(Class<T> clazz, ConfigMap cm) {
        String yaml = cm.getData().get("config");
        try {
            T cluster = parseYaml(clazz, yaml, cm.getMetadata().getName());
            return cluster;
        } catch (NullPointerException npe) {
            throw new IllegalArgumentException("Unable to parse yaml definition of configmap, check if you don't have typo: \n" +
            cm.getData().get("config"));
        }
    }
}
