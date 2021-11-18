package io.radanalytics.operator.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.resource.HasDataHelper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.radanalytics.operator.common.OperatorConfig.ALL_NAMESPACES;

public class ConfigMapWatcher<T extends EntityInfo> extends AbstractWatcher<T> {

    // use via builder
    private ConfigMapWatcher(String namespace,
                             String entityName,
                             KubernetesClient client,
                             Map<String, String> selector,
                             BiConsumer<T, String> onAdd,
                             BiConsumer<T, String> onDelete,
                             BiConsumer<T, String> onModify,
                             Predicate<ConfigMap> predicate,
                             Function<ConfigMap, T> convert) {
        super(true, namespace, entityName, client, null, selector, onAdd, onDelete, onModify, predicate, convert, null);
    }

    public static class Builder<T> {
        private boolean registered = false;
        private String namespace = ALL_NAMESPACES;
        private String entityName;
        private KubernetesClient client;
        private Map<String, String> selector;

        private BiConsumer<T, String> onAdd;
        private BiConsumer<T, String> onDelete;
        private BiConsumer<T, String> onModify;
        private Predicate<ConfigMap> predicate;
        private Function<ConfigMap, T> convert;

        public Builder<T> withNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder<T> withEntityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder<T> withClient(KubernetesClient client) {
            this.client = client;
            return this;
        }

        public Builder<T> withSelector(Map<String, String> selector) {
            this.selector = selector;
            return this;
        }

        public Builder<T> withOnAdd(BiConsumer<T, String> onAdd) {
            this.onAdd = onAdd;
            return this;
        }

        public Builder<T> withOnDelete(BiConsumer<T, String> onDelete) {
            this.onDelete = onDelete;
            return this;
        }

        public Builder<T> withOnModify(BiConsumer<T, String> onModify) {
            this.onModify = onModify;
            return this;
        }

        public Builder<T> withPredicate(Predicate<ConfigMap> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder<T> withConvert(Function<ConfigMap, T> convert) {
            this.convert = convert;
            return this;
        }

        public ConfigMapWatcher build() {
            if (!registered) {
                io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind("v1#ConfigMap", ConfigMap.class);
                registered = true;
            }
            return new ConfigMapWatcher(namespace, entityName, client, selector, onAdd, onDelete, onModify, predicate, convert);
        }
    }

    public static <T extends EntityInfo> T defaultConvert(Class<T> clazz, ConfigMap cm) {
        return HasDataHelper.parseCM(clazz, cm);
    }

    @Override
    public CompletableFuture<ConfigMapWatcher<T>> watch() {
        return createConfigMapWatch().thenApply(watch -> this);
    }
}


