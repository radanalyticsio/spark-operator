package io.radanalytics.operator.common.crd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionFluent;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceSubresourceStatus;
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps;
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
        List<CustomResourceDefinition> crds = client.customResourceDefinitions()
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
                builder = getCRDBuilder(newPrefix,
                                        entityName,
                                        shortNames,
                                        pluralName)
                        .withNewValidation()
                        .withNewOpenAPIV3SchemaLike(schema)
                        .endOpenAPIV3Schema()
                        .endValidation();
            } else {
                builder = getCRDBuilder(newPrefix,
                                        entityName,
                                        shortNames,
                                        pluralName);
            }
            if (additionalPrinterColumnNames != null && additionalPrinterColumnNames.length > 0) {
                for (int i = 0; i < additionalPrinterColumnNames.length; i++) {
                    builder = builder.addNewAdditionalPrinterColumn().withName(additionalPrinterColumnNames[i]).withJSONPath(additionalPrinterColumnPaths[i]).endAdditionalPrinterColumn();
                }
            }
            crdToReturn = builder.endSpec().build();
            try {
                if (schema != null) {
                    // https://github.com/fabric8io/kubernetes-client/issues/1486
                    crdToReturn.getSpec().getValidation().getOpenAPIV3Schema().setDependencies(null);
                }

                client.customResourceDefinitions().createOrReplace(crdToReturn);
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
                client.customResourceDefinitions().createOrReplace(crdToReturn);
            }
        }

        // register the new crd for json serialization
        io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind(newPrefix + "/" + crdToReturn.getSpec().getVersion() + "#" + entityName, InfoClass.class);
        io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind(newPrefix + "/" + crdToReturn.getSpec().getVersion() + "#" + entityName + "List", CustomResourceList.class);

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
                .withApiVersion("apiextensions.k8s.io/v1beta1")
                .withNewMetadata().withName(plural + "." + prefix)
                .endMetadata()
                .withNewSpec()
                    .withNewNames()
                    .withKind(entityName)
                    .withPlural(plural)
                    .withShortNames(Arrays.asList(shortNamesLower)).endNames()
                .withGroup(prefix)
                .withVersion("v1")
                .withScope("Namespaced")
                // add an empty status block to all CRDs created
                .withNewSubresources().withStatus(new CustomResourceSubresourceStatus()).endSubresources();
    }
}
