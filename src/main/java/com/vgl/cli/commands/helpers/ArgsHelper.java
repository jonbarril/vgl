package com.vgl.cli.commands.helpers;

import java.nio.file.Path;
import java.util.List;

public final class ArgsHelper {
    private ArgsHelper() {}

    private static final List<String> FLAGS_WITH_VALUE = List.of(
        "-lr", "-lb", "-bb", "-rr", "-rb"
    );

    public static boolean hasFlag(List<String> args, String flag) {
        return args.contains(flag);
    }

    public static String valueAfterFlag(List<String> args, String flag) {
        int i = args.indexOf(flag);
        if (i < 0) {
            return null;
        }

        int valueIndex = i + 1;
        if (valueIndex >= args.size()) {
            return null;
        }

        String value = args.get(valueIndex);
        if (value.startsWith("-")) {
            return null;
        }

        return value;
    }

    public static Path pathAfterFlag(List<String> args, String flag) {
        String value = valueAfterFlag(args, flag);
        if (value == null) {
            return null;
        }
        return Path.of(value);
    }

    /**
     * Returns a branch name if -bb or -lb is present, preferring -bb.
     * If the flag is present but no value is provided, defaults to "main".
     * Returns null when no branch flags are present.
     */
    public static String branchFromArgsOrNull(List<String> args) {
        if (args.contains("-bb")) {
            String v = valueAfterFlag(args, "-bb");
            return v != null ? v : "main";
        }
        if (args.contains("-lb")) {
            String v = valueAfterFlag(args, "-lb");
            return v != null ? v : "main";
        }
        return null;
    }

    /**
     * Returns the first positional argument (non-flag) that is not the value of a known "flag-with-value".
     * This enables shorthand CLI forms like `vgl delete DIR` while still supporting explicit flags like `-lr DIR`.
     */
    public static String firstPositionalOrNull(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token.startsWith("-")) {
                if (FLAGS_WITH_VALUE.contains(token)) {
                    i++; // skip the value
                }
                continue;
            }

            // token is positional
            return token;
        }
        return null;
    }
}
