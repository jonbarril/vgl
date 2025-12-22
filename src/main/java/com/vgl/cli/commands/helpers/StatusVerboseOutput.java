package com.vgl.cli.commands.helpers;

import java.util.Set;

public class StatusVerboseOutput {
    public static final String HEADER_UNTRACKED = "    -- Untracked Files:";
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
        final String HEADER_UNDECIDED = "    -- Undecided Files:";
        final String HEADER_TRACKED = "    -- Tracked Files:";
        final String HEADER_IGNORED = "    -- Ignored Files:";
        final String NONE = "      (none)";

        System.out.println(HEADER_UNDECIDED);
        if (undecidedSet == null || undecidedSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            boolean anyPrinted = false;
            java.util.List<String> sortedUndecided = new java.util.ArrayList<>(undecidedSet);
            java.util.Collections.sort(sortedUndecided);
            for (String p : sortedUndecided) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("      " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println(NONE);
        }

        System.out.println(HEADER_TRACKED);
        if (trackedSet == null || trackedSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            boolean anyPrinted = false;
            java.util.List<String> sortedTracked = new java.util.ArrayList<>(trackedSet);
            java.util.Collections.sort(sortedTracked);
            for (String p : sortedTracked) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("      " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println(NONE);
        }

        System.out.println(HEADER_UNTRACKED);
        if (untrackedSet == null || untrackedSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            boolean anyPrinted = false;
            java.util.List<String> sortedUntracked = new java.util.ArrayList<>(untrackedSet);
            java.util.Collections.sort(sortedUntracked);
            for (String p : sortedUntracked) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("      " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println(NONE);
        }

        System.out.println(HEADER_IGNORED);
        if (nestedRepos == null || nestedRepos.isEmpty()) {
            System.out.println(NONE);
        } else {
            java.util.List<String> sortedIgnored = new java.util.ArrayList<>(nestedRepos);
            java.util.Collections.sort(sortedIgnored);
            for (String p : sortedIgnored) {
                java.io.File file = new java.io.File(localDir, p);
                if (p.equals(".git")) {
                    System.out.println("      .git (repo)");
                } else if (p.endsWith(" (repo)")) {
                    // Already decorated by StatusCommandHelpers
                    System.out.println("      " + p);
                } else if (file.isDirectory() && new java.io.File(file, ".git").exists()) {
                    System.out.println("      " + (p.endsWith("/") ? p : p + "/") + " (repo)");
                } else {
                    System.out.println("      " + p);
                }
            }
        }
    }

    // Helper to ensure trailing slash for directories
    private static String ensureTrailingSlash(String path, String localDir) {
        java.io.File file = new java.io.File(localDir, path);
        if (file.isDirectory() && !path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }
}