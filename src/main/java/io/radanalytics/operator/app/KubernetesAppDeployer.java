package io.radanalytics.operator.app;

import io.fabric8.kubernetes.api.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.radanalytics.operator.Constants.DEFAULT_SPARK_IMAGE;
import static io.radanalytics.operator.cluster.KubernetesSparkClusterDeployer.env;
import static io.radanalytics.operator.resource.LabelsHelper.OPERATOR_KIND_LABEL;

public class KubernetesAppDeployer {

    private String entityName;
    private String prefix;

    KubernetesAppDeployer(String entityName, String prefix) {
        this.entityName = entityName;
        this.prefix = prefix;
    }

    public KubernetesResourceList getResourceList(AppInfo app, String namespace) {
        String name = app.getName();
        String image = app.getImage();
        String file = app.getMainApplicationFile();
        String main = app.getMainClass();

        ReplicationController submitter = getSubmitterRc(name, image, file, main, namespace);
        KubernetesList resources = new KubernetesListBuilder().withItems(submitter).build();
        return resources;
    }

    private ReplicationController getSubmitterRc(String name, String image, String file, String main,
                                                        String namespace) {
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(env("FOO", "bar"));

        StringBuilder command = new StringBuilder();
        command.append("/opt/spark/bin/spark-submit");
        command.append(" --class ").append(main);
        command.append(" --master k8s://https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT");
        command.append(" --conf spark.kubernetes.namespace=").append(namespace);
        command.append(" --deploy-mode cluster");
        command.append(" --conf spark.app.name=").append(name);
        command.append(" --conf spark.kubernetes.container.image=").append(image);
        command.append(" --conf spark.kubernetes.submission.waitAppCompletion=false");
        command.append(" --conf spark.kubernetes.driver.label.radanalytics.io/app=").append(name);
        command.append(" --conf spark.driver.cores=0.100000");
        command.append(" --conf spark.kubernetes.driver.limit.cores=200m");
        command.append(" --conf spark.driver.memory=512m");
        command.append(" --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark-operator");
        command.append(" --conf spark.kubernetes.driver.label.version=2.3.0 ");
        command.append(" --conf spark.kubernetes.executor.label.radanalytics.io/app=").append(name);
        command.append(" --conf spark.executor.instances=1");
        command.append(" --conf spark.executor.cores=1");
        command.append(" --conf spark.executor.memory=512m");
        command.append(" --conf spark.kubernetes.executor.label.version=2.3.0");
        command.append(" --conf spark.jars.ivy=/tmp/.ivy2");
        command.append(" ").append(file);
        command.append(" && sleep 31536000");

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withEnv(envVars)
                .withImage(DEFAULT_SPARK_IMAGE)
                .withImagePullPolicy("IfNotPresent")
                .withName(name)
                .withTerminationMessagePath("/dev/termination-log")
                .withTerminationMessagePolicy("File")
                .withCommand("/bin/sh", "-c")
                .withArgs(command.toString());

        ReplicationController rc = new ReplicationControllerBuilder().withNewMetadata()
                .withName(name).withLabels(getDefaultLabels(name))
                .endMetadata()
                .withNewSpec().withReplicas(1)
                .withSelector(getDefaultLabels(name))
                .withNewTemplate().withNewMetadata().withLabels(getDefaultLabels(name)).endMetadata()
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
