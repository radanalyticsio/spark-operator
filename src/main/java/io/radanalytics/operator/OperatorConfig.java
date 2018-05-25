/*
 * Copyright 2017-2018, OSHINKO authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.radanalytics.operator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * Cluster Operator configuration
 */
public class OperatorConfig {

    public static final String OSHINKO_NAMESPACE = "OSHINKO_NAMESPACE";
    public static final String OSHINKO_CONFIGMAP_LABELS = "OSHINKO_CONFIGMAP_LABELS";
    public static final String OSHINKO_FULL_RECONCILIATION_INTERVAL_MS = "OSHINKO_FULL_RECONCILIATION_INTERVAL_MS";
    public static final String OSHINKO_OPERATION_TIMEOUT_MS = "OSHINKO_OPERATION_TIMEOUT_MS";

    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_MS = 120_000;
    public static final long DEFAULT_OPERATION_TIMEOUT_MS = 60_000;

    private final Set<String> namespaces;
    private final long reconciliationIntervalMs;
    private final long operationTimeoutMs;

    /**
     * Constructor
     *
     * @param namespaces namespace in which the operator will run and create resources
     * @param reconciliationIntervalMs    specify every how many milliseconds the reconciliation runs
     * @param operationTimeoutMs    timeout for internal operations specified in milliseconds
     */
    public OperatorConfig(Set<String> namespaces, long reconciliationIntervalMs, long operationTimeoutMs) {
        this.namespaces = unmodifiableSet(new HashSet<>(namespaces));
        this.reconciliationIntervalMs = reconciliationIntervalMs;
        this.operationTimeoutMs = operationTimeoutMs;
    }

    /**
     * Loads configuration parameters from a related map
     *
     * @param map   map from which loading configuration parameters
     * @return  Cluster Operator configuration instance
     */
    public static OperatorConfig fromMap(Map<String, String> map) {

        String namespacesList = map.get(OperatorConfig.OSHINKO_NAMESPACE);
        Set<String> namespaces;
        if (namespacesList == null || namespacesList.isEmpty()) {
            namespaces = null; //Collections.singleton("*");
        } else {
            namespaces = new HashSet(asList(namespacesList.trim().split("\\s*,+\\s*")));
        }

        long reconciliationInterval = DEFAULT_FULL_RECONCILIATION_INTERVAL_MS;
        String reconciliationIntervalEnvVar = map.get(OperatorConfig.OSHINKO_FULL_RECONCILIATION_INTERVAL_MS);
        if (reconciliationIntervalEnvVar != null) {
            reconciliationInterval = Long.parseLong(reconciliationIntervalEnvVar);
        }

        long operationTimeout = DEFAULT_OPERATION_TIMEOUT_MS;
        String operationTimeoutEnvVar = map.get(OperatorConfig.OSHINKO_OPERATION_TIMEOUT_MS);
        if (operationTimeoutEnvVar != null) {
            operationTimeout = Long.parseLong(operationTimeoutEnvVar);
        }

        return new OperatorConfig(namespaces, reconciliationInterval, operationTimeout);
    }


    /**
     * @return  namespaces in which the operator runs and creates resources
     */
    public Set<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return  how many milliseconds the reconciliation runs
     */
    public long getReconciliationIntervalMs() {
        return reconciliationIntervalMs;
    }

    /**
     * @return  how many milliseconds should we wait for Kubernetes operations
     */
    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    @Override
    public String toString() {
        return "OperatorConfig(" +
                "namespaces=" + namespaces +
                ",reconciliationIntervalMs=" + reconciliationIntervalMs +
                ")";
    }
}
