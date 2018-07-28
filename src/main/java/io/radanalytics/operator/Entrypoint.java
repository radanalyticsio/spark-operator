package io.radanalytics.operator;

import com.jcabi.manifests.Manifests;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.openshift.client.OpenShiftClient;
import io.radanalytics.operator.app.AppOperator;
import io.radanalytics.operator.cluster.ClusterOperator;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.common.OperatorConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static io.radanalytics.operator.common.AnsiColors.*;

public class Entrypoint {

    private static final Logger log = LoggerFactory.getLogger(Entrypoint.class.getName());

    public static ExecutorService EXECUTORS = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        log.info("Starting..");
        OperatorConfig config = OperatorConfig.fromMap(System.getenv());
        KubernetesClient client = new DefaultKubernetesClient();
        boolean isOpenshift = false;
        try {
            isOpenshift = isOnOpenShift(client);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Failed to distinguish between Kubernetes and OpenShift", e.getCause());
            log.warn("Let's assume we are on K8s");
        }
        run(client, isOpenshift, config).exceptionally(ex -> {
            log.error("Unable to start operator for 1 or more namespace", ex);
            System.exit(1);
            return null;
        });
    }

    private static CompletableFuture<Void> run(KubernetesClient client, boolean isOpenShift, OperatorConfig config) {
        printInfo();

        if (isOpenShift) {
            log.info("OpenShift environment detected.");
        } else {
            log.info("Kubernetes environment detected.");
        }

        List<CompletableFuture> futures = new ArrayList<>();
        if (null == config.getNamespaces()) { // get the current namespace
            String namespace = client.getNamespace();
            CompletableFuture future = runForNamespace(client, isOpenShift, namespace);
            futures.add(future);
        } else {
            for (String namespace : config.getNamespaces()) {
                CompletableFuture future = runForNamespace(client, isOpenShift, namespace);
                futures.add(future);
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
    }

    private static CompletableFuture<Void> runForNamespace(KubernetesClient client, boolean isOpenShift, String namespace) {

        ClusterOperator clusterOperator = new ClusterOperator(namespace, isOpenShift, client);
        AppOperator appOperator = new AppOperator(namespace, isOpenShift, client);

        List<Future> futures = new ArrayList<>();
        Stream.of(clusterOperator, appOperator).forEach(operator -> {
            CompletableFuture<Watch> future = operator.start().thenApply(res -> {
                log.info("{} started in namespace {}", operator.getName(), namespace);
                return res;
            }).exceptionally(e -> {
                log.error("{} in namespace {} failed to start", operator.getName(), namespace, e.getCause());
                System.exit(1);
                return null;
            });
            futures.add(future);
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
    }

    private static boolean isOnOpenShift(KubernetesClient client) throws IOException {
        URL kubernetesApi = client.getMasterUrl();

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.host(kubernetesApi.getHost());

        if (kubernetesApi.getPort() == -1) {
            urlBuilder.port(kubernetesApi.getDefaultPort());
        } else {
            urlBuilder.port(kubernetesApi.getPort());
        }

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        if (kubernetesApi.getProtocol().equals("https")) {
            urlBuilder.scheme("https");
            httpClientBuilder.hostnameVerifier((hostname, session) -> true);
        }
        urlBuilder.addPathSegment("/oapi");

        OkHttpClient httpClient = httpClientBuilder.build();
        HttpUrl url = urlBuilder.build();
        Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();
        boolean success = response.isSuccessful();
        if (success) {
            log.debug("{} returned {}. We are on OpenShift.", url, response.code());
        } else {
            log.debug("{} returned {}. We are not on OpenShift.", url, response.code());
        }
        return success;
    }

    private static void printInfo() {
        String gitSha = "unknown";
        String version = "unknown";
        try {
            gitSha = Manifests.read("Implementation-Build");
            version = Entrypoint.class.getPackage().getImplementationVersion();
        } catch (Exception e) {
            // ignore, not critical
        }
        log.info("\n{}Spark-operator{} has started in version {}{}{}. {}\n", ANSI_R, ANSI_RESET, ANSI_G,
                version, ANSI_RESET, FOO);
        if (!gitSha.isEmpty()) {
            log.info("Git sha: {}{}{}", ANSI_Y, gitSha, ANSI_RESET);
        }
        log.info("==================\n");
    }
}