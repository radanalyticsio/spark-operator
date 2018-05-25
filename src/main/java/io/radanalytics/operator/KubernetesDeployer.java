package io.radanalytics.operator;

import io.fabric8.kubernetes.api.model.*;

import java.util.*;

public class KubernetesDeployer {

    public static final String SPARK_IMAGE_DEFAULT = "radanalyticsio/openshift-spark:2.3-latest";

    public static KubernetesResourceList getResourceList(String name, Optional<String> maybeImage, Optional<Integer> maybeMasters, Optional<Integer> maybeWorkers) {
        String image = maybeImage.orElse(SPARK_IMAGE_DEFAULT);
        int masters = maybeMasters.orElse(1);
        int workers = maybeWorkers.orElse(1);
        ReplicationController masterRc = getRCforMaster(name, masters, image);
        ReplicationController workerRc = getRCforWorker(name, workers, image);
        Service masterService = getServiceForMaster(name, 7077);
        Service masterUiService = getServiceForMaster(name + "-ui", 8080);
        KubernetesList resources = new KubernetesListBuilder().withItems(masterRc, workerRc, masterService, masterUiService).build();
        return resources;
    }

    private static ReplicationController getRCforMaster(String name, int replicas, String image) {
        return getRCforMasterOrWorker(true, name, replicas, image);
    }

    private static ReplicationController getRCforWorker(String name, int replicas, String image) {
        return getRCforMasterOrWorker(false, name, replicas, image);
    }

    private static Service getServiceForMaster(String name, int port) {
        Service masterService = new ServiceBuilder().withNewMetadata().withName(name).endMetadata()
                .withNewSpec().withSelector(getSelector(name + "-m-1"))
                .withPorts(new ServicePortBuilder().withPort(port).withNewTargetPort().withIntVal(port).endTargetPort().withProtocol("TCP").build())
                .endSpec().build();
        return masterService;
    }

    private static EnvVar env(String key, String value) {
        return new EnvVarBuilder().withName(key).withValue(value).build();
    }

    private static ReplicationController getRCforMasterOrWorker(boolean isMaster, String name, int replicas, String image) {
        String masterName = name + (isMaster ? "-m-1" : "-w-1");
        Map<String, String> labels = getSelector(masterName);

        List<ContainerPort> ports = new ArrayList<>(2);
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(env("OSHINKO_SPARK_CLUSTER", name));
        if (isMaster) {
            ContainerPort apiPort = new ContainerPortBuilder().withName("spark-master").withContainerPort(7077).withProtocol("TCP").build();
            ContainerPort uiPort = new ContainerPortBuilder().withName("spark-webui").withContainerPort(8080).withProtocol("TCP").build();
            ports.add(apiPort);
            ports.add(uiPort);
        } else {
            ContainerPort uiPort = new ContainerPortBuilder().withName("spark-webui").withContainerPort(8081).withProtocol("TCP").build();
            ports.add(uiPort);
            envVars.add(env("SPARK_MASTER_ADDRESS", "spark://" + name + ":7077"));
            envVars.add(env("SPARK_MASTER_UI_ADDRESS", "http://" + name + "-ui:8080"));
        }

        Probe masterLiveness = new ProbeBuilder().withNewExec().withCommand(Arrays.asList("/bin/bash", "-c", "curl localhost:8080 | grep -e Status.*ALIVE")).endExec()
                .withFailureThreshold(3)
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(1).build();

        Probe generalProbe = new ProbeBuilder().withFailureThreshold(3).withNewHttpGet()
                .withPath("/")
                .withNewPort().withIntVal(isMaster ? 8080 : 8081).endPort()
                .withScheme("HTTP")
                .endHttpGet()
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(1).build();

        ContainerBuilder containerBuilder = new ContainerBuilder().withEnv(envVars).withImage(image)
                .withImagePullPolicy("IfNotPresent")
                .withName(name + (isMaster ? "-m" : "-w"))
                .withTerminationMessagePath("/dev/termination-log")
                .withTerminationMessagePolicy("File")
                .withPorts(ports);

        if (isMaster) {
            containerBuilder = containerBuilder.withReadinessProbe(generalProbe).withLivenessProbe(masterLiveness);
        } else {
            containerBuilder.withLivenessProbe(generalProbe);
        }

        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata().withName(masterName).endMetadata()
                .withNewSpec().withReplicas(replicas)
                .withSelector(labels)
                .withNewTemplate().withNewMetadata().withLabels(labels).endMetadata()
                .withNewSpec().withContainers(containerBuilder.build())
                .endSpec().endTemplate().endSpec().build();
        return rc;
    }

    private static Map<String, String> getSelector(String name) {
        return Collections.singletonMap("deployment", name);
    }
}
