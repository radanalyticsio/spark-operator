package io.radanalytics.operator.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operator {
    Class<? extends EntityInfo> forKind();
    String named() default "";
    String prefix() default "";
    String[] shortNames() default {};
    String pluralName() default "";
    boolean enabled() default true;
    boolean crd() default true;
    String[] additionalPrinterColumnNames() default {};
    String[] additionalPrinterColumnPaths() default {};
    String[] additionalPrinterColumnTypes() default {};
}
