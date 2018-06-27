package io.radanalytics.operator.common;

public class AnsiColors {
    public static final String ANSI_R = "\u001B[31m";
    public static final String ANSI_G = "\u001B[32m";
    public static final String ANSI_Y = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String BALLOON = "\uD83C\uDF88";
    public static final String FOO = ANSI_R + BALLOON + ANSI_G + BALLOON + ANSI_Y + BALLOON + ANSI_RESET;
}
