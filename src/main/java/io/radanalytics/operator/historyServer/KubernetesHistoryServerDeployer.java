package io.radanalytics.operator.historyServer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
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

    public KubernetesResourceList getResourceList(SparkHistoryServer hs, String namespace) {

        // todo: create deployment, start the history server, put all the config into env variable, expose service if necessary


        String imageRef = getDefaultSparkImage(); // from Constants
        if (cluster.getCustomImage() != null) {
            imageRef = cluster.getCustomImage();
        }

        checkForInjectionVulnerabilities(hs, namespace);
        Map<String, String> defaultLabels = getDefaultLabels(hs.getName());

        new ContainerBuilder().withName("history-server").withImage(hs.getCustomImage())

        new DeploymentBuilder().withNewMetadata().withName(hs.getName()).withLabels(defaultLabels).endMetadata()
                .withNewSpec().withReplicas(1).withNewSelector().withMatchLabels(defaultLabels).endSelector()
                .withNewStrategy().withType("Recreate").endStrategy()
                .withNewTemplate().withNewMetadata().withLabels(defaultLabels).endMetadata()
                .withNewSpec().withServiceAccountName("spark-operator")
                .withContainers(null).endSpec();


        ReplicationController submitter = getSubmitterRc(app, namespace);
        KubernetesList resources = new KubernetesListBuilder().withItems(submitter).build();
        return resources;
    }

    private ReplicationController getSubmitterRc(SparkHistoryServer hs, String namespace) {
        final String name = app.getName();

        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(env("APPLICATION_NAME", name));
        app.getEnv().forEach(kv -> envVars.add(env(kv.getName(), kv.getValue())));

        final DriverSpec driver = Optional.ofNullable(app.getDriver()).orElse(new DriverSpec());
        final ExecutorSpec executor = Optional.ofNullable(app.getExecutor()).orElse(new ExecutorSpec());

        StringBuilder command = new StringBuilder();
        command.append("/opt/spark/bin/spark-submit");
        if (app.getMainClass() != null) {
            command.append(" --class ").append(app.getMainClass());
        }
        command.append(" --master k8s://https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT");
        command.append(" --conf spark.kubernetes.namespace=").append(namespace);
        command.append(" --deploy-mode ").append(app.getMode());
        command.append(" --conf spark.app.name=").append(name);
        command.append(" --conf spark.kubernetes.container.image=").append(app.getImage());
        command.append(" --conf spark.kubernetes.submission.waitAppCompletion=false");
        command.append(" --conf spark.driver.cores=").append(driver.getCores());
        command.append(" --conf spark.kubernetes.driver.limit.cores=").append(driver.getCoreLimit());
        command.append(" --conf spark.driver.memory=").append(driver.getMemory());
        if (driver.getMemoryOverhead() != null) {
            command.append(" --conf spark.driver.memoryOverhead=").append(driver.getMemoryOverhead());
        }
        command.append(" --conf spark.kubernetes.authenticate.driver.serviceAccountName=").append(driver.getServiceAccount());
        command.append(" --conf spark.kubernetes.driver.label.version=2.3.0");

        // common labels
        final Map<String, String> labels = getLabelsForDeletion(name);
        labels.put(prefix + entityName, name);
        if (app.getLabels() != null) labels.putAll(app.getLabels());
        labels.forEach((k, v) -> {
            command.append(" --conf spark.kubernetes.driver.label.").append(k).append("=").append(v);
            command.append(" --conf spark.kubernetes.executor.label.").append(k).append("=").append(v);
        });
        // driver labels
        if (driver.getLabels() != null) {
            driver.getLabels().forEach((k, v) ->
                    command.append(" --conf spark.kubernetes.driver.label.").append(k).append("=").append(v));
        }
        // executor labels
        if (executor.getLabels() != null) {
            executor.getLabels().forEach((k, v) ->
                    command.append(" --conf spark.kubernetes.executor.label.").append(k).append("=").append(v));
        }

        // env
        envVars.forEach(e -> {
            command.append(" --conf spark.kubernetes.driverEnv.").append(e.getName()).append("=").append(e.getValue());
            command.append(" --conf spark.executorEnv.").append(e.getName()).append("=").append(e.getValue());
        });

        command.append(" --conf spark.executor.instances=").append(executor.getInstances());
        command.append(" --conf spark.executor.cores=").append(executor.getCores());
        command.append(" --conf spark.executor.memory=").append(executor.getMemory());
        if (executor.getMemoryOverhead() != null) {
            command.append(" --conf spark.executor.memoryOverhead=").append(executor.getMemoryOverhead());
        }

        // deps
        if (app.getDeps() != null) {
            Deps deps = app.getDeps();
            if (deps.getPyFiles() != null && !deps.getPyFiles().isEmpty()) {
                command.append(" --py-files ").append(deps.getPyFiles().stream().collect(Collectors.joining(",")));
            }
            if (deps.getJars() != null && !deps.getJars().isEmpty()) {
                command.append(" --jars ").append(deps.getJars().stream().collect(Collectors.joining(",")));
            }
            if (deps.getFiles() != null && !deps.getFiles().isEmpty()) {
                command.append(" --files ").append(deps.getFiles().stream().collect(Collectors.joining(",")));
            }
        }

        command.append(" --conf spark.jars.ivy=/tmp/.ivy2");
        // todo: check all the prerequisites
        if (app.getMainApplicationFile() == null) {
            throw new IllegalStateException("mainApplicationFile must be specified");
        }
        command.append(" ").append(app.getMainApplicationFile());

        if (app.getArguments() != null && !app.getArguments().trim().isEmpty()) {
            command.append(" ").append(app.getArguments());
        }

        if (app.getSleep() > 0) {
            command.append(" && echo -e '\\n\\ntask/pod will be rescheduled in ").append(app.getSleep()).append(" seconds..'");
            command.append(" && sleep ").append(app.getSleep());
        }

        final String cmd = "echo -e '\\ncmd:\\n" + command.toString().replaceAll("'", "").replaceAll("--", "\\\\n--") + "\\n\\n' && " + command.toString();
        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withEnv(envVars)
                .withImage(app.getImage())
                .withImagePullPolicy(app.getImagePullPolicy().value())
                .withName(name + "-submitter")
                .withTerminationMessagePath("/dev/termination-log")
                .withTerminationMessagePolicy("File")
                .withCommand("/bin/sh", "-c")
                .withArgs(cmd);

        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata()
                .withName(name + "-submitter").withLabels(getDefaultLabels(name))
                .endMetadata()
                .withNewSpec().withReplicas(1)
                .withSelector(getDefaultLabels(name))
                .withNewTemplate().withNewMetadata().withLabels(getDefaultLabels(name)).withName(name + "-submitter")
                .endMetadata()
                .withNewSpec()
                .withContainers(containerBuilder.build())
                .withServiceAccountName(driver.getServiceAccount())
                .endSpec().endTemplate().endSpec().build();

        return rc;
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

    private void checkForInjectionVulnerabilities(SparkHistoryServer hs, String namespace) {
        //todo: this
    }
}
