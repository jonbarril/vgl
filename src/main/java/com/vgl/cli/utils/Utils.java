package com.vgl.cli.utils;

public final class Utils {
    private Utils() {}

    public static String versionFromRuntime() {
        String version = Utils.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = System.getProperty("vgl.version");
        }
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        return version;
    }
}
