package io.radanalytics.operator.common.crd;
import io.fabric8.kubernetes.api.model.KubernetesResource;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class InfoStatus implements KubernetesResource {

    private String state;
    private String lastTransitionTime;

    private static String toDateString(Date date) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone( "GMT" ));
        return df.format(date);
    }

    public InfoStatus() {
        super();
        this.state = "initial";
        this.lastTransitionTime = toDateString(new Date());
    }
    
    public InfoStatus(String state, Date dt) {
        super();
        this.state = state;
        this.lastTransitionTime = toDateString(dt);
    }

    public void setState(String s) {
        this.state = s;
    }

    public String getState() {
        return this.state;
    }

    public void setLastTransitionTime(Date dt) {
        this.lastTransitionTime = toDateString(dt);
    }

    public String getLastTransitionTime() {
        return this.lastTransitionTime;
    }

    @Override
    public String toString() {
        return "InfoStatus{" +
                " state=" + state +
                " lastTransitionTime=" + lastTransitionTime +
                "}";
    }
}
