package io.radanalytics.operator.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;

import java.io.IOException;
import java.net.URL;

public class JSONSchemaReader {

    public static JSONSchemaProps readSchema(Class infoClass) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        char[] chars = infoClass.getSimpleName().toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        String urlJson = "/schema/" + new String(chars) + ".json";
        String urlJS = "/schema/" + new String(chars) + ".js";
        URL in = infoClass.getResource(urlJson);
        if (null == in) {
            // try also if .js file exists
            in = infoClass.getResource(urlJS);
        }
        if (null == in) {
            return null;
        }
        try {
            return mapper.readValue(in, JSONSchemaProps.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
