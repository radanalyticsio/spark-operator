package io.radanalytics.operator.common.crd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionFluent;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceSubresourceStatus;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.common.JSONSchemaReader;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class CrdDeployer {

    @Inject
    protected Logger log;

    public CustomResourceDefinition initCrds(KubernetesClient client,
                                                    String prefix,
                                                    String entityName,
                                                    String[] shortNames,
                                                    String pluralName,
                                                    String[] additionalPrinterColumnNames,
                                                    String[] additionalPrinterColumnPaths,
                                                    String[] additionalPrinterColumnTypes,
                                                    Class<? extends EntityInfo> infoClass,
                                                    boolean isOpenshift) {
        final String newPrefix = prefix.substring(0, prefix.length() - 1);
        CustomResourceDefinition crdToReturn;

        Serialization.jsonMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<CustomResourceDefinition> crds = client.apiextensions().v1().customResourceDefinitions()
                .list()
                .getItems()
                .stream()
                .filter(p -> entityName.equals(p.getSpec().getNames().getKind()) && newPrefix.equals(p.getSpec().getGroup()))
                .collect(Collectors.toList());
        if (!crds.isEmpty()) {
            crdToReturn = crds.get(0);
            log.info("CustomResourceDefinition for {} has been found in the K8s, so we are skipping the creation.", entityName);
        } else {
            log.info("Creating CustomResourceDefinition for {}.", entityName);
            JSONSchemaProps schema = JSONSchemaReader.readSchema(infoClass);
            CustomResourceDefinitionFluent.SpecNested<CustomResourceDefinitionBuilder> builder;

            if (schema != null) {
                removeDefaultValues(schema);
            }
            builder = getCRDBuilder(newPrefix,
                                    entityName,
                                    shortNames,
                                    pluralName);
            crdToReturn = builder.endSpec().build();
            try {
               client.apiextensions().v1().customResourceDefinitions().createOrReplace(crdToReturn);
            } catch (KubernetesClientException e) {
                // old version of K8s/openshift -> don't use schema validation
                log.warn("Consider upgrading the {}. Your version doesn't support schema validation for custom resources."
                        , isOpenshift ? "OpenShift" : "Kubernetes");
                crdToReturn = getCRDBuilder(newPrefix,
                                            entityName,
                                            shortNames,
                                            pluralName)
                        .endSpec()
                        .build();
                client.apiextensions().v1().customResourceDefinitions().createOrReplace(crdToReturn);
            }
        }

        // register the new crd for json serialization
        io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind(newPrefix + "/" + crdToReturn.getSpec().getVersions().get(0) + "#" + entityName, InfoClass.class);
        io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind(newPrefix + "/" + crdToReturn.getSpec().getVersions().get(0) + "#" + entityName + "List", CustomResourceList.class);

        return crdToReturn;
    }

    private void removeDefaultValues(JSONSchemaProps schema) {
        if (null == schema) {
            return;
        }
        schema.setDefault(null);
        if (null != schema.getProperties()) {
            for (JSONSchemaProps prop : schema.getProperties().values()) {
                removeDefaultValues(prop);
            }
        }
    }

    private CustomResourceDefinitionFluent.SpecNested<CustomResourceDefinitionBuilder> getCRDBuilder(String prefix,
                                                                                                            String entityName,
                                                                                                            String[] shortNames,
                                                                                                            String pluralName) {
        // if no plural name is specified, try to make one by adding "s"
        // also, plural names must be all lowercase
        String plural = pluralName;
        if (plural.isEmpty()) {
            plural = (entityName + "s");
        }
        plural = plural.toLowerCase();

        // short names must be all lowercase
        String[] shortNamesLower = Arrays.stream(shortNames)
                                         .map(sn -> sn.toLowerCase())
                                         .toArray(String[]::new);

        return new CustomResourceDefinitionBuilder()
                .withApiVersion("apiextensions.k8s.io/v1")
                .withNewMetadata().withName(plural + "." + prefix)
                .endMetadata()
                .withNewSpec()
                    .withNewNames()
                    .withKind(entityName)
                    .withPlural(plural)
                    .withShortNames(Arrays.asList(shortNamesLower)).endNames()
                .withGroup(prefix)
                .withScope("Namespaced");
    }
}
