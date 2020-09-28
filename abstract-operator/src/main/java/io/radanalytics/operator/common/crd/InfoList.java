package io.radanalytics.operator.common.crd;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

@JsonDeserialize(using = KubernetesDeserializer.class)
public class InfoList<V> extends CustomResourceList<InfoClass<V>> {
}