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

        // Optional build metadata (e.g., git hash) for dev builds.
        String build = System.getProperty("vgl.build");
        if (build != null) {
            build = build.trim();
            if (!build.isBlank() && !"unknown".equalsIgnoreCase(build)) {
                version = version + "+" + build;
            }
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

    /**
     * Prompt the user to choose one of a small set of single-letter choices.
     *
     * <p>Returns {@code defaultChoice} when the user hits Enter. Returns {@code defaultChoice}
     * when not interactive.
     */
    public static char promptChoice(String prompt, char defaultChoice, char... allowedChoices) {
        char def = Character.toLowerCase(defaultChoice);
        if (!isInteractive()) {
            return def;
        }

        try {
            while (true) {
                System.err.print(prompt);
                System.err.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String line = reader.readLine();
                if (line == null) {
                    return def;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    return def;
                }

                char c = Character.toLowerCase(trimmed.charAt(0));
                for (char a : allowedChoices) {
                    if (c == Character.toLowerCase(a)) {
                        return c;
                    }
                }
                // Unknown choice; re-prompt.
            }
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Print a warning/hint message, and optionally prompt the user for a single-letter choice.
     *
     * <p>In non-interactive mode or when {@code force} is true, no prompt is printed and
     * {@code defaultChoice} is returned.
     */
    public static char warnHintAndMaybePromptChoice(
        String warningAndHintMessage,
        boolean force,
        String prompt,
        char defaultChoice,
        char... allowedChoices
    ) {
        if (warningAndHintMessage != null && !warningAndHintMessage.isBlank()) {
            System.err.println(warningAndHintMessage);
        }
        if (!isInteractive() || force) {
            return Character.toLowerCase(defaultChoice);
        }
        return promptChoice(prompt, defaultChoice, allowedChoices);
    }
}
