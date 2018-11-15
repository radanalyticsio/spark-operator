package io.radanalytics.operator;

public class Constants {

    public static String DEFAULT_SPARK_IMAGE = "quay.io/jkremser/openshift-spark:2.4.0";
    public static String DEFAULT_SPARK_IMAGE_FOR_APP = "quay.io/jkremser/openshift-spark:2.3-latest";
    public static final String OPERATOR_TYPE_UI_LABEL = "ui";
    public static final String OPERATOR_TYPE_MASTER_LABEL = "master";
    public static final String OPERATOR_TYPE_WORKER_LABEL = "worker";
}
