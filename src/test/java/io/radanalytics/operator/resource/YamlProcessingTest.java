package io.radanalytics.operator.resource;


import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.types.SparkCluster;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class YamlProcessingTest {

    private String path1 = "examples/cluster-cm.yaml";
    private String path2 = "examples/with-prepared-data.yaml";
    private String path3 = "examples/cluster-with-config.yaml";
    private String cluster1;
    private String cluster2;
    private String cluster3;
    private KubernetesClient client = new DefaultKubernetesClient();

    @Before
    public void prepare() throws IOException {
//        this.cluster1 = readFile(path1);
//        this.cluster2 = readFile(path2);
        ConfigMap cm1 = client.configMaps().load(path1).get();
        ConfigMap cm2 = client.configMaps().load(path2).get();
        ConfigMap cm3 = client.configMaps().load(path3).get();
        this.cluster1 = cm1.getData().get("config");
        this.cluster2 = cm2.getData().get("config");
        this.cluster3 = cm3.getData().get("config");
    }

    @Test
    public void testParseCM1() {
        ConfigMap cm1 = client.configMaps().load(path1).get();
        SparkCluster clusterInfo = HasDataHelper.parseCM(SparkCluster.class, cm1);

        assertEquals(clusterInfo.getName(), "my-spark-cluster");
        assertEquals(clusterInfo.getWorker().getInstances().intValue(), 2);
        assertEquals(clusterInfo.getMaster().getInstances().intValue(), 1);
    }

    @Test
    public void testParseCM2() {
        ConfigMap cm2 = client.configMaps().load(path2).get();
        SparkCluster clusterInfo = HasDataHelper.parseCM(SparkCluster.class, cm2);

        assertNull(clusterInfo.getMaster());
        assertEquals(clusterInfo.getDownloadData().size(), 2);
        assertEquals(clusterInfo.getDownloadData().get(0).getTo(), "/tmp/");
    }

    @Test
    public void testParseCM3() {
        ConfigMap cm3 = client.configMaps().load(path3).get();
        SparkCluster clusterInfo = HasDataHelper.parseCM(SparkCluster.class, cm3);

        assertEquals(clusterInfo.getMaster().getInstances().intValue(), 1);
        assertEquals(clusterInfo.getSparkConfiguration().size(), 2);
        assertEquals(clusterInfo.getSparkConfiguration().get(0).getName(), "spark.executor.memory");

        assertEquals(clusterInfo.getEnv().size(), 2);
        assertEquals(clusterInfo.getEnv().get(0).getName(), "SPARK_WORKER_CORES");
        assertEquals(clusterInfo.getEnv().get(0).getValue(), "2");

        assertEquals(clusterInfo.getSparkConfigurationMap(), "my-config");
    }

    @Test
    public void testParseYaml1() {
        SparkCluster clusterInfo = HasDataHelper.parseYaml(SparkCluster.class, cluster1, "foo");

        assertEquals(clusterInfo.getName(), "foo");
        assertEquals(clusterInfo.getWorker().getInstances().intValue(), 2);
        assertEquals(clusterInfo.getCustomImage(), null);
    }

    @Test
    public void testParseYaml2() {
        SparkCluster clusterInfo = HasDataHelper.parseYaml(SparkCluster.class, cluster2, "bar");

        assertEquals(clusterInfo.getName(), "bar");
        assertNull(clusterInfo.getMaster());
        assertEquals(clusterInfo.getDownloadData().size(), 2);
    }

    @Test
    public void testParseGeneral() {
        SparkCluster clusterInfo1 = HasDataHelper.parseYaml(SparkCluster.class, cluster1, "foobar");
        ConfigMap cm1 = client.configMaps().load(path1).get();

        SparkCluster clusterInfo2 = HasDataHelper.parseCM(SparkCluster.class, cm1);
        SparkCluster clusterInfo3 = HasDataHelper.parseYaml(SparkCluster.class, cluster1, "my-spark-cluster");

        // different name
        assertNotEquals(clusterInfo1, clusterInfo2);

        assertEquals(clusterInfo2.getName(), clusterInfo3.getName());
    }

    private String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

}
