/*
 * Copyright 2018
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.cluster.ClusterInfo;
import io.radanalytics.operator.common.EntityInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A helper for parsing the data section inside the K8s resource
 */
public class HasDataHelper {
    private static final Logger log = LoggerFactory.getLogger(HasDataHelper.class.getName());

    public static <T extends EntityInfo> T parseYaml(Class<T> clazz, String yamlDoc, String name) {
        Yaml snake = new Yaml(new Constructor(clazz));
        T cluster = null;
        try {
            cluster = snake.load(yamlDoc);
        } catch (YAMLException ex) {
            String msg = "Unable to parse yaml definition of configmap, check if you don't have typo: \n'\n" +
                    yamlDoc + "\n'\n";
            log.error(msg);
        }
        if (cluster == null) {
            String msg = "Unable to parse yaml definition of configmap, check if you don't have typo: \n'\n" +
                    yamlDoc + "\n'\n";
            log.error(msg);
            try {
                cluster = clazz.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        cluster.setName(name);
        return cluster;
    }

    public static <T extends EntityInfo> T parseCM(Class<T> clazz, ConfigMap cm) {
        String yaml = cm.getData().get("config");
        T cluster = parseYaml(clazz, yaml, cm.getMetadata().getName());
        return cluster;
    }
}
