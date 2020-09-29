package io.radanalytics.operator.common.crd;

import io.fabric8.kubernetes.client.CustomResource;

public class InfoClass<U> extends CustomResource {
    private U spec;
    private InfoStatus status;

    public InfoClass() {
        this.status = new InfoStatus();
    }

    public InfoStatus getStatus() {
        return this.status;
    }

    public void setStatus(InfoStatus status) {
        this.status = status;
    }

    public U getSpec() {
        return spec;
    }

    public void setSpec(U spec) {
        this.spec = spec;
    }
}
