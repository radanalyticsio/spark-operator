package io.radanalytics.operator.resource;


import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.radanalytics.operator.ClusterInfo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.radanalytics.operator.OperatorConfig.DEFAULT_SPARK_IMAGE;
import static org.junit.Assert.*;

public class YamlProcessingTest {

    private String path1 = "examples/cluster.yaml";
    private String path2 = "examples/with-prepared-data.yaml";
    private String cluster1;
    private String cluster2;
    private KubernetesClient client = new DefaultKubernetesClient();

    @Before
    public void prepare() throws IOException {
//        this.cluster1 = readFile(path1);
//        this.cluster2 = readFile(path2);
        ConfigMap cm1 = client.configMaps().load(path1).get();
        ConfigMap cm2 = client.configMaps().load(path2).get();
        this.cluster1 = cm1.getData().get("config");
        this.cluster2 = cm2.getData().get("config");
    }

    @Test
    public void testParseCM1() {
        ConfigMap cm1 = client.configMaps().load(path1).get();
        ClusterInfo clusterInfo = HasDataHelper.parseCM(cm1);

        assertEquals(clusterInfo.getName(), "my-spark-cluster");
        assertEquals(clusterInfo.getWorkerNodes(), 2);
        assertEquals(clusterInfo.getMasterNodes(), 1);
    }

    @Test
    public void testParseCM2() {
        ConfigMap cm2 = client.configMaps().load(path2).get();
        ClusterInfo clusterInfo = HasDataHelper.parseCM(cm2);

        assertEquals(clusterInfo.getMasterNodes(), 1);
        assertEquals(clusterInfo.getDownloadData().size(), 2);
        assertEquals(clusterInfo.getDownloadData().get(0).getTo(), "/tmp/");
    }

    @Test
    public void testParseYaml1() {
        ClusterInfo clusterInfo = HasDataHelper.parseYaml(cluster1, "foo");

        assertEquals(clusterInfo.getName(), "foo");
        assertEquals(clusterInfo.getWorkerNodes(), 2);
        assertEquals(clusterInfo.getCustomImage(), DEFAULT_SPARK_IMAGE);
    }

    @Test
    public void testParseYaml2() {
        ClusterInfo clusterInfo = HasDataHelper.parseYaml(cluster2, "bar");

        assertEquals(clusterInfo.getName(), "bar");
        assertEquals(clusterInfo.getMasterNodes(), 1);
        assertEquals(clusterInfo.getDownloadData().size(), 2);
    }

    @Test
    public void testParseGeneral() {
        ClusterInfo clusterInfo1 = HasDataHelper.parseYaml(cluster1, "foobar");
        ConfigMap cm1 = client.configMaps().load(path1).get();

        ClusterInfo clusterInfo2 = HasDataHelper.parseCM(cm1);
        ClusterInfo clusterInfo3 = HasDataHelper.parseYaml(cluster1, "my-spark-cluster");

        // different name
        assertNotEquals(clusterInfo1, clusterInfo2);

        assertEquals(clusterInfo2, clusterInfo3);
    }

    private String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

}
