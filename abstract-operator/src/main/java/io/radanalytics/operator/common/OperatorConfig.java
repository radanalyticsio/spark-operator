/*
 * Copyright 2017-2018
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator.common;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * Operator configuration
 */
public class OperatorConfig {

    public static final String WATCH_NAMESPACE = "WATCH_NAMESPACE";
    public static final String SAME_NAMESPACE = "~";
    public static final String ALL_NAMESPACES = "*";
    public static final String METRICS = "METRICS";
    public static final String METRICS_JVM = "METRICS_JVM";
    public static final String METRICS_PORT = "METRICS_PORT";
    public static final String FULL_RECONCILIATION_INTERVAL_S = "FULL_RECONCILIATION_INTERVAL_S";
    public static final String OPERATOR_OPERATION_TIMEOUT_MS = "OPERATOR_OPERATION_TIMEOUT_MS";

    public static final boolean DEFAULT_METRICS = true;
    public static final boolean DEFAULT_METRICS_JVM = false;
    public static final int DEFAULT_METRICS_PORT = 8080;
    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_S = 180;
    public static final long DEFAULT_OPERATION_TIMEOUT_MS = 60_000;

    private final Set<String> namespaces;
    private final boolean metrics;
    private final boolean metricsJvm;
    private final int metricsPort;
    private final long reconciliationIntervalS;
    private final long operationTimeoutMs;

    /**
     * Constructor
     *
     * @param namespaces                  namespace in which the operator will run and create resources
     * @param metrics                     whether the metrics server for prometheus should be started
     * @param metricsJvm                  whether to expose the internal JVM metrics, like heap, # of threads, etc.
     * @param metricsPort                 on which port the metrics server should be listening
     * @param reconciliationIntervalS     specify every how many milliseconds the reconciliation runs
     * @param operationTimeoutMs          timeout for internal operations specified in milliseconds
     */
    public OperatorConfig(Set<String> namespaces, boolean metrics, boolean metricsJvm, int metricsPort,
                          long reconciliationIntervalS, long operationTimeoutMs) {
        this.namespaces = namespaces;
        this.reconciliationIntervalS = reconciliationIntervalS;
        this.operationTimeoutMs = operationTimeoutMs;
        this.metrics = metrics;
        this.metricsJvm = metricsJvm;
        this.metricsPort = metricsPort;
    }

    /**
     * Loads configuration parameters from a related map
     *
     * @param map   map from which loading configuration parameters
     * @return  Cluster Operator configuration instance
     */
    public static OperatorConfig fromMap(Map<String, String> map) {

        String namespacesList = map.get(WATCH_NAMESPACE);

        Set<String> namespaces;
        if (namespacesList == null || namespacesList.isEmpty()) {
            // empty WATCH_NAMESPACE means we will be watching all the namespaces
            namespaces = Collections.singleton(ALL_NAMESPACES);
        } else {
            namespaces = new HashSet<>(asList(namespacesList.trim().split("\\s*,+\\s*")));
            namespaces = namespaces.stream().map(
                    ns -> ns.startsWith("\"") && ns.endsWith("\"") ? ns.substring(1, ns.length() - 1) : ns)
                    .collect(Collectors.toSet());
        }

        boolean metricsAux = DEFAULT_METRICS;
        String metricsEnvVar = map.get(METRICS);
        if (metricsEnvVar != null) {
            metricsAux = !"false".equals(metricsEnvVar.trim().toLowerCase());
        }

        boolean metricsJvmAux = DEFAULT_METRICS_JVM;
        int metricsPortAux = DEFAULT_METRICS_PORT;
        if (metricsAux) {
            String metricsJvmEnvVar = map.get(METRICS_JVM);
            if (metricsJvmEnvVar != null) {
                metricsJvmAux = "true".equals(metricsJvmEnvVar.trim().toLowerCase());
            }
            String metricsPortEnvVar = map.get(METRICS_PORT);
            if (metricsPortEnvVar != null) {
                metricsPortAux = Integer.parseInt(metricsPortEnvVar.trim().toLowerCase());
            }
        }

        long reconciliationInterval = DEFAULT_FULL_RECONCILIATION_INTERVAL_S;
        String reconciliationIntervalEnvVar = map.get(FULL_RECONCILIATION_INTERVAL_S);
        if (reconciliationIntervalEnvVar != null) {
            reconciliationInterval = Long.parseLong(reconciliationIntervalEnvVar);
        }

        long operationTimeout = DEFAULT_OPERATION_TIMEOUT_MS;
        String operationTimeoutEnvVar = map.get(OPERATOR_OPERATION_TIMEOUT_MS);
        if (operationTimeoutEnvVar != null) {
            operationTimeout = Long.parseLong(operationTimeoutEnvVar);
        }

        return new OperatorConfig(namespaces, metricsAux, metricsJvmAux, metricsPortAux, reconciliationInterval,
                operationTimeout);
    }


    /**
     * @return  namespaces in which the operator runs and creates resources
     */
    public Set<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return  how many seconds among the reconciliation runs
     */
    public long getReconciliationIntervalS() {
        return reconciliationIntervalS;
    }

    /**
     * @return  how many milliseconds should we wait for Kubernetes operations
     */
    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    public boolean isMetrics() {
        return metrics;
    }

    public boolean isMetricsJvm() {
        return metricsJvm;
    }

    public int getMetricsPort() {
        return metricsPort;
    }

    @Override
    public String toString() {
        return "OperatorConfig{" +
                "namespaces=" + namespaces +
                ", metrics=" + metrics +
                ", metricsJvm=" + metricsJvm +
                ", metricsPort=" + metricsPort +
                ", reconciliationIntervalS=" + reconciliationIntervalS +
                ", operationTimeoutMs=" + operationTimeoutMs +
                '}';
    }
}
