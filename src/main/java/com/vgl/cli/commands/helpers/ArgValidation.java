package com.vgl.cli.commands.helpers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ArgValidation {
    private ArgValidation() {}

    public static boolean isValidCreateArgs(List<String> args) {
        return validateArgs(args, Spec.CREATE);
    }

    public static boolean isValidDeleteArgs(List<String> args) {
        return validateArgs(args, Spec.DELETE);
    }

    private static boolean validateArgs(List<String> args, Spec spec) {
        if (args == null) {
            return false;
        }

        Set<String> seen = new HashSet<>();
        int positionalCount = 0;

        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null || token.isBlank()) {
                return false;
            }

            if (token.startsWith("-")) {
                if (!spec.allowedFlags.contains(token)) {
                    return false;
                }

                if (!seen.add(token) && !spec.repeatableFlags.contains(token)) {
                    return false;
                }

                if (spec.flagsWithRequiredValue.contains(token)) {
                    if (i + 1 >= args.size()) {
                        return false;
                    }
                    String value = args.get(i + 1);
                    if (value == null || value.isBlank() || value.startsWith("-")) {
                        return false;
                    }
                    i++; // consume value
                    continue;
                }

                if (spec.flagsWithOptionalValue.contains(token)) {
                    if (i + 1 < args.size()) {
                        String value = args.get(i + 1);
                        if (value != null && !value.isBlank() && !value.startsWith("-")) {
                            i++; // consume optional value
                        }
                    }
                    continue;
                }

                continue;
            }

            // Positional
            positionalCount++;
            if (positionalCount > spec.maxPositionals) {
                return false;
            }
        }

        // Disallow mixing -lr DIR with positional DIR.
        if (spec.flagsWithRequiredValue.contains("-lr") && args.contains("-lr")) {
            if (positionalCount > 0) {
                return false;
            }
        }

        return true;
    }

    private record Spec(
        Set<String> allowedFlags,
        Set<String> flagsWithRequiredValue,
        Set<String> flagsWithOptionalValue,
        Set<String> repeatableFlags,
        int maxPositionals
    ) {
        private static final Spec CREATE = new Spec(
            Set.of("-f", "-lr", "-lb", "-bb", "-rr", "-rb"),
            Set.of("-lr", "-rr"),
            Set.of("-lb", "-bb", "-rb"),
            Set.of(),
            1
        );

        private static final Spec DELETE = new Spec(
            Set.of("-f", "-lr", "-lb", "-bb", "-rr", "-rb"),
            Set.of("-lr", "-rr"),
            Set.of("-lb", "-bb", "-rb"),
            Set.of(),
            1
        );
    }
}
