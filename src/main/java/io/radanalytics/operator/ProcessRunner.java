package io.radanalytics.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Optional;

public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class.getName());

    public void createCluster(String name, Optional<String> image, Optional<Integer> masters, Optional<Integer> workers) {
        StringBuilder sb = getCommonParams();
        image.ifPresent(value -> sb.append(" --image=").append(value));
        masters.ifPresent(value -> sb.append(" --masters=").append(value));
        workers.ifPresent(value -> sb.append(" --workers=").append(value));
        runOshinko("create " + name + sb.toString());
    }

    public void deleteCluster(String name) {
        StringBuilder sb = getCommonParams();
        runOshinko("delete " + name + sb.toString());
    }

    public void scaleCluster(String name, int workers) {
        StringBuilder sb = getCommonParams();
        sb.append(" --workers=").append(workers);
        runOshinko("scale " + name + sb.toString());
    }

    private void runOshinko(String suffix) {
        try {
            String[] command = new String[] {"sh", "-c", "sh -c \"/oshinko_linux_386/oshinko " + suffix + "\""};
            log.info("running: {}", Arrays.toString(command));
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line + "\n");
            }
            String stdOutput = sb.toString();
            if (!stdOutput.isEmpty()) {
                log.info(stdOutput);
            }
            in.close();

            sb = new StringBuilder();
            while ((line = err.readLine()) != null) {
                sb.append(line + "\n");
            }
            String errOutput = sb.toString();
            if (!errOutput.isEmpty()) {
                log.error(errOutput);
            }
            err.close();
        } catch (IOException e) {
            log.error("Running oshinko cli failed with: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private StringBuilder getCommonParams() {
        StringBuilder sb = new StringBuilder();
        sb.append(" --certificate-authority=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
        sb.append(" --token=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`");
        sb.append(" --namespace=`cat /var/run/secrets/kubernetes.io/serviceaccount/namespace`");
        sb.append(" --server=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT");
        return sb;
    }
}
