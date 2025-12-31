package com.vgl.cli.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    public static boolean isInteractive() {
        if (Boolean.parseBoolean(System.getProperty("vgl.noninteractive", "false"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("vgl.force.interactive", "false"))) {
            return true;
        }
        return System.console() != null;
    }

    public static boolean confirm(String prompt) {
        if (!isInteractive()) {
            return false;
        }
        try {
            System.err.print(prompt);
            System.err.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (line == null) {
                return false;
            }
            String trimmed = line.trim().toLowerCase();
            return trimmed.equals("y") || trimmed.equals("yes");
        } catch (Exception e) {
            return false;
        }
    }
}
