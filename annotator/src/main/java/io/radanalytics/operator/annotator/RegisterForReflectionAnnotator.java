package io.radanalytics.operator.annotator;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JDefinedClass;
import org.jsonschema2pojo.AbstractAnnotator;
import io.quarkus.runtime.annotations.RegisterForReflection;

public class RegisterForReflectionAnnotator extends AbstractAnnotator {

    @Override
    public void propertyOrder(JDefinedClass clazz, JsonNode propertiesNode) {
        super.propertyOrder(clazz, propertiesNode);
        clazz.annotate(RegisterForReflection.class);
    }
}