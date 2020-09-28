/*
 * Copyright 2018
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.resource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.radanalytics.operator.common.EntityInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A helper for parsing the data section inside the K8s resource (ConfigMap).
 * Type parameter T represents the concrete EntityInfo that captures the configuration obout the
 * objects in the clusters we are interested in, be it spark clusters, http servers, certificates, etc.
 *
 * One can create arbitrarily deep configurations by nesting the types in <code>Class&lt;T&gt;</code> and using
 * the Snake yaml or other library as for conversions between YAML and Java objects.
 */
public class HasDataHelper {
    private static final Logger log = LoggerFactory.getLogger(HasDataHelper.class.getName());

    public static <T extends EntityInfo> T parseYaml(Class<T> clazz, String yamlDoc, String name) {

        LoaderOptions options = new LoaderOptions();
        Yaml snake = new Yaml(new Constructor(clazz));
        T entity = null;
        try {
            entity = snake.load(yamlDoc);
        } catch (YAMLException ex) {
            String msg = "Unable to parse yaml definition of configmap, check if you don't have typo: \n'\n" +
                    yamlDoc + "\n'\n";
            log.error(msg);
            throw new IllegalStateException(ex);
        }
        if (entity == null) {
            String msg = "Unable to parse yaml definition of configmap, check if you don't have typo: \n'\n" +
                    yamlDoc + "\n'\n";
            log.error(msg);
            try {
                entity = clazz.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        if (entity != null && entity.getName() == null) {
            entity.setName(name);
        }
        return entity;
    }

    /**
     *
     * @param clazz  concrete class of type T that extends the EntityInfo.
     *               This is the resulting type, we are convertion into.
     * @param cm     input config map that will be converted into T.
     *               We assume there is a multi-line section in the config map called config and it
     *               contains a YAML structure that represents the object of type T. In other words the
     *               keys in the yaml should be the same as the field names in the class T and the name of the
     *               configmap will be assigned to the name of the object T. One can create arbitrarily deep
     *               configuration by nesting the types in T and using the Snake yaml as the conversion library.
     * @param <T>    type parameter (T must extend {@link io.radanalytics.operator.common.EntityInfo})
     * @return       Java object of type T
     */
    public static <T extends EntityInfo> T parseCM(Class<T> clazz, ConfigMap cm) {
        String yaml = cm.getData().get("config");
        T entity = parseYaml(clazz, yaml, cm.getMetadata().getName());
        return entity;
    }
}
