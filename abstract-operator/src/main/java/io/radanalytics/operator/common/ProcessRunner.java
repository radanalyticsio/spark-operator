package io.radanalytics.operator.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import static io.radanalytics.operator.common.AnsiColors.*;

/**
 * Helper class that can be used from the concrete operators as the glue code for running the OS process.
 */
public class ProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(AbstractOperator.class.getName());

    public static void runPythonScript(String path) {
        runCommand("python3 " + path);
    }

    public static void runShellScript(String path) {
        runCommand(path);
    }


    public static void runCommand(String command) {
        try {
            String[] commands = new String[] {"sh", "-c", "\"" + command + "\""};
            log.info("running: {}", Arrays.toString(commands));
            Process p = Runtime.getRuntime().exec(commands);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line + "\n");
            }
            String stdOutput = sb.toString();
            if (!stdOutput.isEmpty()) {
                log.info("{}{}{}", gr(), stdOutput, xx());
            }
            in.close();

            sb = new StringBuilder();
            while ((line = err.readLine()) != null) {
                sb.append(line + "\n");
            }
            String errOutput = sb.toString();
            if (!errOutput.isEmpty()) {
                log.error("{}{}{}", re(), stdOutput, xx());
            }
            err.close();
        } catch (IOException e) {
            log.error("Running '{}' failed with: {}", command, e.getMessage());
            e.printStackTrace();
        }
    }
}
