package io.radanalytics.operator.cluster;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.resource.LabelsHelper;
import io.radanalytics.types.ClusterInfo;
import io.radanalytics.types.DownloadDatum;
import io.radanalytics.types.Env;
import io.radanalytics.types.SparkConfiguration;

import java.util.*;

import static io.radanalytics.operator.Constants.OPERATOR_TYPE_MASTER_LABEL;
import static io.radanalytics.operator.Constants.OPERATOR_TYPE_UI_LABEL;
import static io.radanalytics.operator.Constants.OPERATOR_TYPE_WORKER_LABEL;
import static io.radanalytics.operator.resource.LabelsHelper.*;

public class KubernetesSparkClusterDeployer {
    private KubernetesClient client;
    private String entityName;
    private String prefix;

    KubernetesSparkClusterDeployer(KubernetesClient client, String entityName, String prefix) {
        this.client = client;
        this.entityName = entityName;
        this.prefix = prefix;
    }

    public KubernetesResourceList getResourceList(ClusterInfo cluster) {
        synchronized (this.client) {
            String name = cluster.getName();
            ReplicationController masterRc = getRCforMaster(cluster);
            ReplicationController workerRc = getRCforWorker(cluster);
            Service masterService = getService(false, name, 7077);
            Service masterUiService = getService(true, name, 8080);
            KubernetesList resources = new KubernetesListBuilder().withItems(masterRc, workerRc, masterService, masterUiService).build();
            return resources;
        }
    }

    private ReplicationController getRCforMaster(ClusterInfo cluster) {
        return getRCforMasterOrWorker(true, cluster);
    }

    private ReplicationController getRCforWorker(ClusterInfo cluster) {
        return getRCforMasterOrWorker(false, cluster);
    }

    private Service getService(boolean isUi, String name, int port) {
        Map<String, String> labels = getDefaultLabels(name);
        labels.put(prefix + LabelsHelper.OPERATOR_SEVICE_TYPE_LABEL, isUi ? OPERATOR_TYPE_UI_LABEL : OPERATOR_TYPE_WORKER_LABEL);
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

    private ReplicationController getRCforMasterOrWorker(boolean isMaster, ClusterInfo cluster) {
        String name = cluster.getName();
        String podName = name + (isMaster ? "-m" : "-w");
        Map<String, String> selector = getSelector(name, podName);

        List<ContainerPort> ports = new ArrayList<>(2);
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(env("OSHINKO_SPARK_CLUSTER", name));
        cluster.getEnv().forEach(kv -> {
            envVars.add(env(kv.getName(), kv.getValue()));
        });
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

        ContainerBuilder containerBuilder = new ContainerBuilder().withEnv(envVars).withImage(cluster.getCustomImage())
                .withImagePullPolicy("IfNotPresent")
                .withName(name + (isMaster ? "-m" : "-w"))
                .withTerminationMessagePath("/dev/termination-log")
                .withTerminationMessagePolicy("File")
                .withPorts(ports);

        // limits
        if (cluster.getMemory() != null || cluster.getCpu() != null) {
            Map<String, Quantity> limits = new HashMap<>(2);
            if (cluster.getMemory() != null) {
                limits.put("memory", new Quantity(cluster.getMemory()));
            }
            if (cluster.getCpu() != null) {
                limits.put("cpu", new Quantity(cluster.getCpu()));
            }
            containerBuilder.withResources(new ResourceRequirements(limits, limits));
        }


        if (isMaster) {
            containerBuilder = containerBuilder.withReadinessProbe(generalProbe).withLivenessProbe(masterLiveness);
        } else {
            containerBuilder.withLivenessProbe(generalProbe);
        }

        Map<String, String> labels = getDefaultLabels(name);
        labels.put(prefix + LabelsHelper.OPERATOR_RC_TYPE_LABEL, isMaster ? OPERATOR_TYPE_MASTER_LABEL : OPERATOR_TYPE_WORKER_LABEL);

        Map<String, String> podLabels = getSelector(name, podName);
        podLabels.put(prefix + LabelsHelper.OPERATOR_POD_TYPE_LABEL, isMaster ? OPERATOR_TYPE_MASTER_LABEL : OPERATOR_TYPE_WORKER_LABEL);
        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata()
                .withName(podName).withLabels(labels)
                .endMetadata()
                .withNewSpec().withReplicas(isMaster ? cluster.getMasterNodes() : cluster.getWorkerNodes())
                .withSelector(selector)
                .withNewTemplate().withNewMetadata().withLabels(podLabels).endMetadata()
                .withNewSpec().withContainers(containerBuilder.build())
                .endSpec().endTemplate().endSpec().build();

        final boolean cmExists = cmExists(cluster.getSparkConfigurationMap());
        if (!cluster.getDownloadData().isEmpty() || !cluster.getSparkConfiguration().isEmpty() || cmExists) {
            addInitContainers(rc, cluster, cmExists);
        }
        return rc;
    }

    private ReplicationController addInitContainers(ReplicationController rc,
                                                           ClusterInfo cluster,
                                                           boolean cmExists) {
        final List<DownloadDatum> downloadData = cluster.getDownloadData();
        final List<SparkConfiguration> config = cluster.getSparkConfiguration();
        final boolean needInitContainer = !downloadData.isEmpty() || !config.isEmpty();
        final StringBuilder command = new StringBuilder();
        if (needInitContainer) {
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
            if (cmExists) {
                command.append("cp /tmp/config/* /opt/spark/conf");
                command.append(" && ");
            }
            if (!config.isEmpty()) {
                command.append("echo -e \"");
                config.forEach(kv -> {
                    command.append(kv.getName());
                    command.append(" ");
                    command.append(kv.getValue());
                    command.append("\\n");
                });
                command.append("\" >> /opt/spark/conf/spark-defaults.conf");
                command.append(" && ");
            }
            command.delete(command.length() - 4, command.length());
        }

        final VolumeMount m1 = new VolumeMountBuilder().withName("data-dir").withMountPath("/tmp").build();
        final VolumeMount m2 = new VolumeMountBuilder().withName("configmap-dir").withMountPath("/tmp/config").build();
        final VolumeMount m3 = new VolumeMountBuilder().withName("conf-dir").withMountPath("/opt/spark/conf").build();
        final Volume v1 = new VolumeBuilder().withName("data-dir").withNewEmptyDir().endEmptyDir().build();
        final Volume v2 = new VolumeBuilder().withName("configmap-dir").withNewConfigMap().withName(cluster.getSparkConfigurationMap()).endConfigMap().build();
        final Volume v3 = new VolumeBuilder().withName("conf-dir").withNewEmptyDir().endEmptyDir().build();
        final List<VolumeMount> mounts = new ArrayList<>(2);
        final List<Volume> volumes = new ArrayList<>(2);
        if (!downloadData.isEmpty()) {
            mounts.add(m1);
            volumes.add(v1);
        }
        if (cmExists) {
            mounts.add(m2);
            volumes.add(v2);
        }
        if (cmExists || !config.isEmpty()) {
            mounts.add(m3);
            volumes.add(v3);
        }
        PodSpec spec = rc.getSpec().getTemplate().getSpec();
        if (needInitContainer) {
            Container initContainer = new ContainerBuilder()
                    .withName("downloader")
                    .withImage("busybox")
                    .withCommand("/bin/sh", "-c")
                    .withArgs(command.toString())
                    .withVolumeMounts(mounts)
                    .build();
            spec.setInitContainers(Arrays.asList(initContainer));
        }
        spec.getContainers().get(0).setVolumeMounts(mounts);
        spec.setVolumes(volumes);
        rc.getSpec().getTemplate().setSpec(spec);
        return rc;
    }

    private boolean cmExists(String name) {
        ConfigMap configMap = client.configMaps().withName(name).get();
        return configMap != null && configMap.getData() != null && !configMap.getData().isEmpty();
    }

    private Map<String, String> getSelector(String clusterName, String podName) {
        Map<String, String> map = getDefaultLabels(clusterName);
        map.put(prefix + LabelsHelper.OPERATOR_DEPLOYMENT_LABEL, podName);
        return map;
    }

    public Map<String, String> getDefaultLabels(String name) {
        Map<String, String> map = new HashMap<>(3);
        map.put(prefix + OPERATOR_KIND_LABEL, entityName);
        map.put(prefix + entityName, name);
        return map;
    }
}
