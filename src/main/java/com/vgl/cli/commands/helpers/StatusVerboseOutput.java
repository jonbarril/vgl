package com.vgl.cli.commands.helpers;

import java.util.Set;

public class StatusVerboseOutput {
    public static void printVerbose(Set<String> trackedSet, Set<String> untrackedSet, Set<String> undecidedSet, Set<String> nestedRepos, String localDir, java.util.List<String> filters) {
        java.util.function.Predicate<String> matchesFilter = (p) -> {
            if (filters == null || filters.isEmpty()) return true;
            for (String f : filters) {
                if (f.contains("*") || f.contains("?")) {
                    String regex = f.replace(".", "\\.").replace("*", ".*").replace("?", ".");
                    if (p.matches(regex)) return true;
                } else {
                    if (p.equals(f) || p.startsWith(f + "/") || p.contains("/" + f)) return true;
                }
            }
            return false;
        };

        // Order: Undecided, Tracked, Untracked, Ignored
        System.out.println("  -- Undecided Files:");
        if (undecidedSet == null || undecidedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            boolean anyPrinted = false;
            for (String p : undecidedSet) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
        }

        System.out.println("  -- Tracked Files:");
        if (trackedSet == null || trackedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            boolean anyPrinted = false;
            for (String p : trackedSet) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
        }

        System.out.println("  -- Untracked Files:");
        if (untrackedSet == null || untrackedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            boolean anyPrinted = false;
            for (String p : untrackedSet) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
        }

        System.out.println("  -- Ignored Files:");
        if (nestedRepos == null) nestedRepos = java.util.Collections.emptySet();
        if (trackedSet == null) trackedSet = java.util.Collections.emptySet();
        if (untrackedSet == null) untrackedSet = java.util.Collections.emptySet();
        if (undecidedSet == null) undecidedSet = java.util.Collections.emptySet();
        // Compose the full ignored set: all files in ignored, minus those in tracked/untracked/undecided
        java.util.Set<String> ignoredSet = new java.util.LinkedHashSet<>(nestedRepos);
        // If nestedRepos is actually the Ignored set, this is a no-op, but if not, add all ignored files
        // (In current code, nestedRepos is actually the Ignored set)
        // For robustness, print all entries in nestedRepos (which is the Ignored set)
        java.util.List<String> sorted = new java.util.ArrayList<>(ignoredSet);
        java.util.Collections.sort(sorted);
        if (sorted.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String p : sorted) {
                java.io.File file = new java.io.File(localDir, p);
                if (p.equals(".git")) {
                    System.out.println("  .git (repo)");
                } else if (p.endsWith(" (repo)")) {
                    // Already decorated by StatusCommandHelpers
                    System.out.println("  " + p);
                } else if (file.isDirectory() && new java.io.File(file, ".git").exists()) {
                    System.out.println("  " + (p.endsWith("/") ? p : p + "/") + " (repo)");
                } else {
                    System.out.println("  " + p);
                }
            }
        }
    }

    private static String ensureTrailingSlash(String path, String localDir) {
        java.io.File file = new java.io.File(localDir, path);
        if (file.isDirectory() && !path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }
}
