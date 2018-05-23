package io.radanalytics.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class.getName());

    public void createCluster(String name, Optional<String> image, Optional<Integer> masters, Optional<Integer> workers) {
        StringBuilder sb = new StringBuilder();
        sb.append(" --certificate-authority=/var/run/secrets /kubernetes.io/serviceaccount/ca.crt");
        sb.append(" --token=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`");
        sb.append(" --namespace=`cat /var/run/secrets/kubernetes.io/serviceaccount/namespace`");
        sb.append(" --server=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT");
        image.ifPresent(value -> sb.append(" --image=").append(value));
        masters.ifPresent(value -> sb.append(" --masters=").append(value));
        workers.ifPresent(value -> sb.append(" --workers=").append(value));
        runOshinko("create " + name + sb.toString());
    }

    public void deleteCluster(String name) {
        runOshinko("delete " + name);
    }

    private void runOshinko(String suffix) {
        try {
            String command = "/oshinko_linux_386/oshinko " + suffix;
            log.info("running: " + command);
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            log.error("Running oshinko cli failed with: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
