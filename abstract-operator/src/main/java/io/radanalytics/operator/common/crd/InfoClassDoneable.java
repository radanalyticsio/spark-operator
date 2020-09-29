package io.radanalytics.operator.common.crd;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;


public class InfoClassDoneable<S> extends CustomResourceDoneable<InfoClass<S>> {
    public InfoClassDoneable(InfoClass<S> resource, Function function) {
        super(resource, function);
    }
}
