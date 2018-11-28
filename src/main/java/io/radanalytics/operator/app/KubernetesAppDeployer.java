package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.*;
import io.radanalytics.types.NodeSpec;
import io.radanalytics.types.SparkApplication;

import java.util.*;

import static io.radanalytics.operator.cluster.KubernetesSparkClusterDeployer.env;
import static io.radanalytics.operator.resource.LabelsHelper.OPERATOR_KIND_LABEL;

public class KubernetesAppDeployer {

    private String entityName;
    private String prefix;

    KubernetesAppDeployer(String entityName, String prefix) {
        this.entityName = entityName;
        this.prefix = prefix;
    }

    public KubernetesResourceList getResourceList(SparkApplication app, String namespace) {
        ReplicationController submitter = getSubmitterRc(app, namespace);
        KubernetesList resources = new KubernetesListBuilder().withItems(submitter).build();
        return resources;
    }

    private ReplicationController getSubmitterRc(SparkApplication app, String namespace) {
        List<EnvVar> envVars = new ArrayList<>();

        final String name = app.getName();

        final NodeSpec driver = Optional.ofNullable(app.getDriver()).orElse(new NodeSpec());
        final NodeSpec executor = Optional.ofNullable(app.getDriver()).orElse(new NodeSpec());

        // todo: vulnerable to injection

        StringBuilder command = new StringBuilder();
        command.append("/opt/spark/bin/spark-submit");
        command.append(" --class ").append(app.getMainClass());
        command.append(" --master k8s://https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT");
        command.append(" --conf spark.kubernetes.namespace=").append(namespace);
        command.append(" --deploy-mode ").append(app.getMode());
        command.append(" --conf spark.app.name=").append(name);
        command.append(" --conf spark.kubernetes.container.image=").append(app.getImage());
        command.append(" --conf spark.kubernetes.submission.waitAppCompletion=false");
        command.append(" --conf spark.kubernetes.driver.label.radanalytics.io/sparkapplication=").append(name);
        command.append(" --conf spark.driver.cores=").append(driver.getCores());
        command.append(" --conf spark.kubernetes.driver.limit.cores=").append(driver.getCoreLimit());
        command.append(" --conf spark.driver.memory=").append(driver.getMemory());
        command.append(" --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark-operator");
        command.append(" --conf spark.kubernetes.driver.label.version=2.3.0 ");

        // todo: custom labels

        command.append(" --conf spark.kubernetes.executor.label.radanalytics.io/sparkapplication=").append(name);
        command.append(" --conf spark.executor.instances=").append(executor.getInstances());
        command.append(" --conf spark.executor.cores=").append(executor.getCores());
        command.append(" --conf spark.executor.memory=").append(executor.getMemory());
        command.append(" --conf spark.kubernetes.executor.label.version=2.3.0");
        command.append(" --conf spark.jars.ivy=/tmp/.ivy2");
        command.append(" ").append(app.getMainApplicationFile());

        if (app.getSleep() > 0) {
            command.append(" && echo -e '\\n\\ntask/pod will be rescheduled in ").append(app.getSleep()).append(" seconds..'");
            command.append(" && sleep ").append(app.getSleep());
        }

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withEnv(envVars)
                .withImage(app.getImage())
                .withImagePullPolicy("IfNotPresent")
                .withName(name + "-submitter")
                .withTerminationMessagePath("/dev/termination-log")
                .withTerminationMessagePolicy("File")
                .withCommand("/bin/sh", "-c")
                .withArgs(command.toString());

        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata()
                .withName(name + "-submitter").withLabels(getDefaultLabels(name))
                .endMetadata()
                .withNewSpec().withReplicas(1)
                .withSelector(getDefaultLabels(name))
                .withNewTemplate().withNewMetadata().withLabels(getDefaultLabels(name)).withName(name + "-submitter")
                .endMetadata()
                .withNewSpec()
                .withContainers(containerBuilder.build())
                .withServiceAccountName("spark-operator")
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
}
