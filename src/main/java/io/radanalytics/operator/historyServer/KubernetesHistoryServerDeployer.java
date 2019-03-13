package io.radanalytics.operator.historyServer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentFluent;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.types.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.radanalytics.operator.Constants.getDefaultSparkImage;
import static io.radanalytics.operator.cluster.KubernetesSparkClusterDeployer.env;
import static io.radanalytics.operator.resource.LabelsHelper.OPERATOR_KIND_LABEL;

public class KubernetesHistoryServerDeployer {

    private String entityName;
    private String prefix;

    KubernetesHistoryServerDeployer(String entityName, String prefix) {
        this.entityName = entityName;
        this.prefix = prefix;
    }

    public KubernetesResourceList getResourceList(SparkHistoryServer hs, String namespace, boolean isOpenshift) {

        checkForInjectionVulnerabilities(hs, namespace);
        Map<String, String> defaultLabels = getDefaultLabels(hs.getName());

        List<HasMetadata> resources = new ArrayList<>();

        Container historyServerContainer = new ContainerBuilder().withName("history-server")
                .withImage(Optional.ofNullable(hs.getCustomImage()).orElse(getDefaultSparkImage()))
                .withCommand(Arrays.asList("/bin/sh", "-c"))
                .withArgs("mkdir /tmp/spark-events && /entrypoint ls && /opt/spark/bin/spark-class") // todo: shared path on fs
                .withEnv(env("SPARK_HISTORY_OPTS", "-Dspark.history.ui.port=9001"))
                .withPorts(new ContainerPortBuilder().withName("web-ui").withContainerPort(9001).build())
                .build();

        Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(hs.getName()).withLabels(defaultLabels).endMetadata()
                .withNewSpec().withReplicas(1).withNewSelector().withMatchLabels(defaultLabels).endSelector()
                .withNewStrategy().withType("Recreate").endStrategy()
                .withNewTemplate().withNewMetadata().withLabels(defaultLabels).endMetadata()
                .withNewSpec().withServiceAccountName("spark-operator")
                .withContainers(historyServerContainer).endSpec().endTemplate().endSpec().build();
        resources.add(deployment);

        if (hs.getExpose()) {
            Service service = new ServiceBuilder().withNewMetadata().withLabels(defaultLabels).withName(hs.getName())
                    .endMetadata().withNewSpec().withSelector(defaultLabels)
                    .withPorts(new ServicePortBuilder().withName("web-ui").withPort(9001).build()).endSpec().build();
            resources.add(service);
            if (isOpenshift) {
                Ingress ingress = new IngressBuilder().withNewMetadata().withName(hs.getName())
                        .withLabels(defaultLabels).endMetadata()
                        .withNewSpec().withRules(new IngressRuleBuilder().withNewHttp()
                                .withPaths(new HTTPIngressPathBuilder().withNewBackend().withServiceName(hs.getName()).withNewServicePort(9001).endBackend().build()).endHttp().build())
                        .endSpec().build();
                resources.add(ingress);
            }
        }

        KubernetesList k8sResources = new KubernetesListBuilder().withItems(resources).build();
        return k8sResources;
    }

    public Map<String, String> getDefaultLabels(String name) {
        Map<String, String> map = new HashMap<>(3);
        map.put(prefix + OPERATOR_KIND_LABEL, entityName);
        map.put(prefix + entityName, name);
        return map;
    }

    public Map<String, String> getLabelsForDeletion(String name) {
        Map<String, String> map = new HashMap<>(2);
        map.put(prefix + entityName, name);
        return map;
    }

    public static EnvVar env(String key, String value) {
        return new EnvVarBuilder().withName(key).withValue(value).build();
    }

    private void checkForInjectionVulnerabilities(SparkHistoryServer hs, String namespace) {
        //todo: this
    }
}
