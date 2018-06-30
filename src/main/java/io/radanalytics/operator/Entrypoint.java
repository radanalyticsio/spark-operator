package io.radanalytics.operator;

import com.jcabi.manifests.Manifests;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.radanalytics.operator.app.AppOperator;
import io.radanalytics.operator.cluster.ClusterOperator;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.common.OperatorConfig;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.radanalytics.operator.common.AnsiColors.*;

public class Entrypoint {

    private static final Logger log = LoggerFactory.getLogger(Entrypoint.class.getName());

    public static void main(String[] args) {
        OperatorConfig config = OperatorConfig.fromMap(System.getenv());
        Vertx vertx = Vertx.vertx();
        KubernetesClient client = new DefaultKubernetesClient();

        isOnOpenShift(vertx, client).setHandler(os -> {
            if (os.succeeded()) {
                run(vertx, client, os.result().booleanValue(), config).setHandler(ar -> {
                    if (ar.failed()) {
                        log.error("Unable to start operator for 1 or more namespace", ar.cause());
                        System.exit(1);
                    }
                });
            } else {
                log.error("Failed to distinguish between Kubernetes and OpenShift", os.cause());
                System.exit(1);
            }
        });
    }

    private static CompositeFuture run(Vertx vertx, KubernetesClient client, boolean isOpenShift, OperatorConfig config) {
        printInfo();

        if (isOpenShift) {
            log.info("OpenShift environment detected.");
        } else {
            log.info("Kubernetes environment detected.");
        }

        List<Future> futures = new ArrayList<>();
        if (null == config.getNamespaces()) { // get the current namespace
            String namespace = client.getNamespace();
            Future future = runForNamespace(vertx, client, isOpenShift, namespace);
            futures.add(future);
        } else {
            for (String namespace : config.getNamespaces()) {
                Future future = runForNamespace(vertx, client, isOpenShift, namespace);
                futures.add(future);
            }
        }
        return CompositeFuture.join(futures);
    }

    private static Future runForNamespace(Vertx vertx, KubernetesClient client, boolean isOpenShift, String namespace) {

        ClusterOperator clusterOperator = new ClusterOperator(namespace, isOpenShift, client);
        AppOperator appOperator = new AppOperator(namespace, isOpenShift, client);

        List<Future> futures = new ArrayList<>();
        Stream.of(clusterOperator, appOperator).forEach(operator -> {
            Future<String> future = Future.future();
            futures.add(future);
            vertx.deployVerticle(operator,
                    res -> {
                        if (res.succeeded()) {
                            log.info("{} verticle started in namespace {}", operator.getName(), namespace);
                        } else {
                            log.error("{} verticle in namespace {} failed to start", operator.getName(), namespace, res.cause());
                            System.exit(1);
                        }
                        future.completer().handle(res);
                    });
        });
        return CompositeFuture.join(futures);
    }

    private static Future<Boolean> isOnOpenShift(Vertx vertx, KubernetesClient client)  {
        URL kubernetesApi = client.getMasterUrl();
        Future<Boolean> fut = Future.future();

        HttpClientOptions httpClientOptions = new HttpClientOptions();
        httpClientOptions.setDefaultHost(kubernetesApi.getHost());

        if (kubernetesApi.getPort() == -1) {
            httpClientOptions.setDefaultPort(kubernetesApi.getDefaultPort());
        } else {
            httpClientOptions.setDefaultPort(kubernetesApi.getPort());
        }

        if (kubernetesApi.getProtocol().equals("https")) {
            httpClientOptions.setSsl(true);
            httpClientOptions.setTrustAll(true);
        }

        HttpClient httpClient = vertx.createHttpClient(httpClientOptions);

        httpClient.getNow("/oapi", res -> {
            if (res.statusCode() == 200) {
                log.debug("{} returned {}. We are on OpenShift.", res.request().absoluteURI(), res.statusCode());
                // We should be on OpenShift based on the /oapi result. We can now safely try isAdaptable() to be 100% sure.
                Boolean isOpenShift = Boolean.TRUE.equals(client.isAdaptable(OpenShiftClient.class));
                fut.complete(isOpenShift);
            } else {
                log.debug("{} returned {}. We are not on OpenShift.", res.request().absoluteURI(), res.statusCode());
                fut.complete(Boolean.FALSE);
            }
        });

        return fut;
    }

    private static void printInfo() {
        String gitSha = Manifests.read("Implementation-Build");
        log.info("\n{}Spark-operator{} has started in version {}{}{}. {}\n", ANSI_R, ANSI_RESET, ANSI_G,
                Entrypoint.class.getPackage().getImplementationVersion(), ANSI_RESET, FOO);
        if (!gitSha.isEmpty()) {
            log.info("Git sha: {}{}{}", ANSI_Y, gitSha, ANSI_RESET);
        }
        log.info("==================\n");
    }
}