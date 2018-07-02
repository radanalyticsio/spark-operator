package io.radanalytics.operator.cluster;

import io.fabric8.kubernetes.api.model.*;
import io.radanalytics.operator.cluster.ClusterInfo;

import java.util.*;

import static io.radanalytics.operator.resource.LabelsHelper.*;

public class KubernetesSparkClusterDeployer {

    public static KubernetesResourceList getResourceList(ClusterInfo cluster) {
        String name = cluster.getName();
        String image = cluster.getCustomImage();
        int masters = cluster.getMasterNodes();
        int workers = cluster.getWorkerNodes();
        List<ClusterInfo.DL> downloadData = cluster.getDownloadData();
        ReplicationController masterRc = getRCforMaster(name, masters, image, downloadData);
        ReplicationController workerRc = getRCforWorker(name, workers, image, downloadData);
        Service masterService = getService(false, name, 7077);
        Service masterUiService = getService(true, name, 8080);
        KubernetesList resources = new KubernetesListBuilder().withItems(masterRc, workerRc, masterService, masterUiService).build();
        return resources;
    }

    private static ReplicationController getRCforMaster(String name, int replicas, String image, List<ClusterInfo.DL> downloadData) {
        return getRCforMasterOrWorker(true, name, replicas, image, downloadData);
    }

    private static ReplicationController getRCforWorker(String name, int replicas, String image, List<ClusterInfo.DL> downloadData) {
        return getRCforMasterOrWorker(false, name, replicas, image, downloadData);
    }

    private static Service getService(boolean isUi, String name, int port) {
        Map<String, String> labels = getDefaultLabels(name);
        labels.put(OPERATOR_SEVICE_TYPE_LABEL, isUi ? OPERATOR_TYPE_UI_LABEL : OPERATOR_TYPE_WORKER_LABEL);
        Service masterService = new ServiceBuilder().withNewMetadata().withName(isUi ? name + "-ui" : name)
                .withLabels(labels).endMetadata()
                .withNewSpec().withSelector(getSelector(name, name + "-m"))
                .withPorts(new ServicePortBuilder().withPort(port).withNewTargetPort()
                        .withIntVal(port).endTargetPort().withProtocol("TCP").build())
                .endSpec().build();
        return masterService;
    }

    public static EnvVar env(String key, String value) {
        return new EnvVarBuilder().withName(key).withValue(value).build();
    }

    private static ReplicationController getRCforMasterOrWorker(boolean isMaster, String name, int replicas, String image, List<ClusterInfo.DL> downloadData) {
        String podName = name + (isMaster ? "-m" : "-w");
        Map<String, String> selector = getSelector(name, podName);

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

        Map<String, String> labels = getDefaultLabels(name);
        labels.put(OPERATOR_RC_TYPE_LABEL, isMaster ? OPERATOR_TYPE_MASTER_LABEL : OPERATOR_TYPE_WORKER_LABEL);

        Map<String, String> podLabels = getSelector(name, podName);
        podLabels.put(OPERATOR_POD_TYPE_LABEL, isMaster ? OPERATOR_TYPE_MASTER_LABEL : OPERATOR_TYPE_WORKER_LABEL);
        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata()
                .withName(podName).withLabels(labels)
                .endMetadata()
                .withNewSpec().withReplicas(replicas)
                .withSelector(selector)
                .withNewTemplate().withNewMetadata().withLabels(podLabels).endMetadata()
                .withNewSpec().withContainers(containerBuilder.build())
                .endSpec().endTemplate().endSpec().build();

        if (!downloadData.isEmpty()) {
            addInitContainers(rc, downloadData);
        }
        return rc;
    }

    private static ReplicationController addInitContainers(ReplicationController rc, List<ClusterInfo.DL> downloadData) {
        VolumeMount mount = new VolumeMountBuilder().withName("data-dir").withMountPath("/tmp").build();

        StringBuilder command = new StringBuilder();
        downloadData.forEach(dl -> {
            String url = dl.getUrl();
            String to = dl.getTo();
            // if 'to' ends with slash, we know it's a directory and we use the -P switch to change the prefix,
            // otherwise using -O for renaming the downloaded file
            String param = to.endsWith("/") ? " -P " : " -O ";
            command.append("wget ");
            command.append(url);
            command.append(param);
            command.append(to);
            command.append(" && ");
        });
        command.delete(command.length() - 4, command.length());

        Container initContainer = new ContainerBuilder()
                .withName("downloader")
                .withImage("busybox")
                .withCommand("/bin/sh", "-c")
                .withArgs(command.toString())
                .withVolumeMounts(mount)
                .build();
        Volume volume = new VolumeBuilder().withName("data-dir").withNewEmptyDir().endEmptyDir().build();

        PodSpec spec = rc.getSpec().getTemplate().getSpec();
        spec.getContainers().get(0).setVolumeMounts(Arrays.asList(mount));
        spec.setInitContainers(Arrays.asList(initContainer));
        spec.setVolumes(Arrays.asList(volume));
        rc.getSpec().getTemplate().setSpec(spec);
        return rc;
    }

    private static Map<String, String> getSelector(String clusterName, String podName) {
        Map<String, String> map = getDefaultLabels(clusterName);
        map.put(OPERATOR_DEPLOYMENT_LABEL, podName);
        return map;
    }

    public static Map<String, String> getDefaultLabels(String name) {
        Map<String, String> map = new HashMap<>(3);
        map.put(OPERATOR_KIND_LABEL, OPERATOR_KIND_APP_LABEL);
        map.put(OPERATOR_DOMAIN + OPERATOR_KIND_CLUSTER_LABEL, name);
        return map;
    }
}
