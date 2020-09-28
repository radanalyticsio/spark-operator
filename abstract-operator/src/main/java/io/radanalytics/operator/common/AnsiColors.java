package io.radanalytics.operator.common;

public class AnsiColors {

    // these shouldn't be used directly
    private static final String ANSI_R = "\u001B[31m";
    private static final String ANSI_G = "\u001B[32m";
    private static final String ANSI_Y = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";

    // if empty, it's true
    public static final boolean COLORS = !"false".equals(System.getenv("COLORS"));

    public static String re() {
        return COLORS ? ANSI_R : "";
    }

    public static String gr() {
        return COLORS ? ANSI_G : "";
    }

    public static String ye() {
        return COLORS ? ANSI_Y : "";
    }

    public static String xx() {
        return COLORS ? ANSI_RESET : "";
    }
}
