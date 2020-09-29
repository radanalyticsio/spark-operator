package io.radanalytics.operator;

import com.jcabi.manifests.Manifests;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.log4j.InstrumentedAppender;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.radanalytics.operator.common.AbstractOperator;
import io.radanalytics.operator.common.AnsiColors;
import io.radanalytics.operator.common.EntityInfo;
import io.radanalytics.operator.common.OperatorConfig;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.radanalytics.operator.common.AnsiColors.*;
import static io.radanalytics.operator.common.OperatorConfig.ALL_NAMESPACES;
import static io.radanalytics.operator.common.OperatorConfig.SAME_NAMESPACE;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Entry point class that watches on StartupEvent and should bootstrap all the registered operators
 * that are present on the class path. It scans the class path for those classes that have the
 * {@link io.radanalytics.operator.common.Operator} annotations on them or extends the {@link AbstractOperator}.
 */
@ApplicationScoped
public class SDKEntrypoint {
    private static ExecutorService executors;

    protected OperatorConfig config;
    protected KubernetesClient client;
    protected boolean isOpenShift;

    @Inject
    private Logger log;


    @Inject @Any
    private Instance<AbstractOperator<? extends EntityInfo>> operators;

    public SDKEntrypoint() {

    }

    /* this entrypoint can be called from an environment w/o CDI */
    public SDKEntrypoint(Logger log) {
        this.log = log;
        init();
    }

    @PostConstruct
    void init(){
        config = OperatorConfig.fromMap(System.getenv());
        client = new DefaultKubernetesClient();
        checkIfOnOpenshift();
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("Stopped");
    }

    public void onStart(@Observes StartupEvent event) {
        log.info("Starting..");
        CompletableFuture<Void> future = run().exceptionally(ex -> {
            log.error("Unable to start operator for one or more namespaces", ex);
            System.exit(1);
            return null;
        });
        if (config.isMetrics()) {
            CompletableFuture<Optional<HTTPServer>> maybeMetricServer = future.thenCompose(s -> runMetrics());
        }
    }

    private CompletableFuture<Void> run() {
        printInfo();
        if (isOpenShift) {
            log.info("{}OpenShift{} environment detected.", AnsiColors.ye(), AnsiColors.xx());
        } else {
            log.info("{}Kubernetes{} environment detected.", AnsiColors.ye(), AnsiColors.xx());
        }

        List<CompletableFuture> futures = new ArrayList<>();
        if (operators != null) {
            if (SAME_NAMESPACE.equals(config.getNamespaces().iterator().next())) { // current namespace
                String namespace = client.getNamespace();
                CompletableFuture future = runForNamespace(isOpenShift, namespace, config.getReconciliationIntervalS(), 0);
                futures.add(future);
            } else {
                if (ALL_NAMESPACES.equals(config.getNamespaces().iterator().next())) {
                    CompletableFuture future = runForNamespace(isOpenShift, ALL_NAMESPACES, config.getReconciliationIntervalS(), 0);
                    futures.add(future);
                } else {
                    Iterator<String> ns;
                    int i;
                    for (ns = config.getNamespaces().iterator(), i = 0; i < config.getNamespaces().size(); i++) {
                        CompletableFuture future = runForNamespace(isOpenShift, ns.next(), config.getReconciliationIntervalS(), i);
                        futures.add(future);
                    }
                }
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
    }

    private CompletableFuture<Optional<HTTPServer>> runMetrics() {
        HTTPServer httpServer = null;
        try {
            log.info("Starting a simple HTTP server for exposing internal metrics..");
            httpServer = new HTTPServer(config.getMetricsPort());
            log.info("{}metrics server{} listens on port {}", AnsiColors.ye(), AnsiColors.xx(), config.getMetricsPort());
        } catch (IOException e) {
            log.error("Can't start metrics server because of: {} ", e.getMessage());
            e.printStackTrace();
        }
        if (config.isMetricsJvm()) {
            DefaultExports.initialize();
        }
        final Optional<HTTPServer> maybeServer = Optional.of(httpServer);
        return CompletableFuture.supplyAsync(() -> maybeServer);
    }

    private CompletableFuture<Void> runForNamespace(boolean isOpenShift, String namespace, long reconInterval, int delay) {
        List<AbstractOperator<? extends EntityInfo>> operatorList = operators.stream().collect(Collectors.toList());

        if (operatorList.isEmpty()) {
            log.warn("No suitable operators were found, make sure your class extends AbstractOperator and have @Singleton on it.");
        }

        List<Future> futures = new ArrayList<>();
        final int operatorNumber = operatorList.size();
        IntStream.range(0, operatorNumber).forEach(operatorIndex -> {
            AbstractOperator operator = operatorList.get(operatorIndex);
            if (!AbstractOperator.class.isAssignableFrom(operator.getClass())) {
                log.error("Class {} annotated with @Operator doesn't extend the AbstractOperator", operator.getClass());
                return; // do not fail
            }

            if (!operator.isEnabled()) {
                log.info("Skipping initialization of {} operator", operator.getClass());
                return;
            }

            operator.setClient(client);
            operator.setNamespace(namespace);
            operator.setOpenshift(isOpenShift);

            CompletableFuture<Watch> future = operator.start().thenApply(res -> {
                log.info("{} started in namespace {}", operator.getName(), namespace);
                return res;
            }).exceptionally(ex -> {
                log.error("{} in namespace {} failed to start", operator.getName(), namespace, ((Throwable) ex).getCause());
                System.exit(1);
                return null;
            });

            ScheduledExecutorService s = Executors.newScheduledThreadPool(1);
            int realDelay = (delay * operatorNumber) + operatorIndex + 2;
            ScheduledFuture<?> scheduledFuture =
                    s.scheduleAtFixedRate(() -> {
                        try {
                            operator.fullReconciliation();
                            operator.setFullReconciliationRun(true);
                        } catch (Throwable t) {
                            log.warn("error during full reconciliation: {}", t.getMessage());
                            t.printStackTrace();
                        }
                    }, realDelay, reconInterval, SECONDS);
            log.info("full reconciliation for {} scheduled (periodically each {} seconds)", operator.getName(), reconInterval);
            log.info("the first full reconciliation for {} is happening in {} seconds", operator.getName(), realDelay);

            futures.add(future);
        });
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
    }

    private void checkIfOnOpenshift() {
        try {
            URL kubernetesApi = client.getMasterUrl();

            HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
            urlBuilder.host(kubernetesApi.getHost());

            if (kubernetesApi.getPort() == -1) {
                urlBuilder.port(kubernetesApi.getDefaultPort());
            } else {
                urlBuilder.port(kubernetesApi.getPort());
            }
            if (kubernetesApi.getProtocol().equals("https")) {
                urlBuilder.scheme("https");
            }
            urlBuilder.addPathSegment("apis/route.openshift.io/v1");

            OkHttpClient httpClient = HttpClientUtils.createHttpClient(new ConfigBuilder().build());
            HttpUrl url = urlBuilder.build();
            Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();
            boolean success = response.isSuccessful();
            if (success) {
                log.info("{} returned {}. We are on OpenShift.", url, response.code());
            } else {
                log.info("{} returned {}. We are not on OpenShift. Assuming, we are on Kubernetes.", url, response.code());
            }
            isOpenShift = success;
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Failed to distinguish between Kubernetes and OpenShift");
            log.warn("Let's assume we are on K8s");
            isOpenShift = false;
        }
    }

    private void printInfo() {
        String gitSha = "unknown";
        String version = "unknown";
        try {
            version = Optional.ofNullable(SDKEntrypoint.class.getPackage().getImplementationVersion()).orElse(version);
            gitSha = Optional.ofNullable(Manifests.read("Implementation-Build")).orElse(gitSha);
        } catch (Exception e) {
            // ignore, not critical
        }

        if(config.isMetrics()) {
            registerMetrics(gitSha, version);
        }

        log.info("\n{}Operator{} has started in version {}{}{}.\n", re(), xx(), gr(),
                version, xx());
        if (!gitSha.isEmpty()) {
            log.info("Git sha: {}{}{}", ye(), gitSha, xx());
        }
        log.info("==================\n");
    }

    private void registerMetrics(String gitSha, String version) {
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();

        labels.addAll(Arrays.asList("gitSha", "version",
                "CRD",
                "COLORS",
                OperatorConfig.WATCH_NAMESPACE,
                OperatorConfig.METRICS,
                OperatorConfig.METRICS_JVM,
                OperatorConfig.METRICS_PORT,
                OperatorConfig.FULL_RECONCILIATION_INTERVAL_S,
                OperatorConfig.OPERATOR_OPERATION_TIMEOUT_MS
        ));
        values.addAll(Arrays.asList(gitSha, version,
                Optional.ofNullable(System.getenv().get("CRD")).orElse("true"),
                Optional.ofNullable(System.getenv().get("COLORS")).orElse("true"),
                SAME_NAMESPACE.equals(config.getNamespaces().iterator().next()) ? client.getNamespace() : config.getNamespaces().toString(),
                String.valueOf(config.isMetrics()),
                String.valueOf(config.isMetricsJvm()),
                String.valueOf(config.getMetricsPort()),
                String.valueOf(config.getReconciliationIntervalS()),
                String.valueOf(config.getOperationTimeoutMs())
        ));

        Gauge.build()
                .name("operator_info")
                .help("Basic information about the abstract operator library.")
                .labelNames(labels.toArray(new String[]{}))
                .register()
                .labels(values.toArray(new String[]{}))
                .set(1);

        // add log appender for metrics
        final org.apache.log4j.Logger rootLogger = org.apache.log4j.Logger.getRootLogger();
        InstrumentedAppender metricsLogAppender = new InstrumentedAppender();
        metricsLogAppender.setName("metrics");
        rootLogger.addAppender(metricsLogAppender);
    }

    public static ExecutorService getExecutors() {
        if (null == executors) {
            executors = Executors.newFixedThreadPool(10);
        }
        return executors;
    }

    public boolean isOpenShift() {
        return isOpenShift;
    }

    public OperatorConfig getConfig() {
        return config;
    }

    public KubernetesClient getClient() {
        return client;
    }
}
