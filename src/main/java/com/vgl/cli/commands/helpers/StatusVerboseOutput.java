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
        if (nestedRepos == null || nestedRepos.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String p : nestedRepos) {
                java.io.File file = new java.io.File(localDir, p);
                if (file.isDirectory() && new java.io.File(file, ".git").exists()) {
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
