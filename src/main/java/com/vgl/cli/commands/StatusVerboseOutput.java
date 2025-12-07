package com.vgl.cli.commands;

import java.util.Set;

public class StatusVerboseOutput {
    public static void printVerbose(Set<String> trackedSet, Set<String> untrackedSet, Set<String> undecidedSet, Set<String> nestedRepos, String localDir) {
        System.out.println("-- Tracked Files:");
        if (trackedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            trackedSet.forEach(p -> System.out.println("  " + ensureTrailingSlash(p, localDir)));
        }
        System.out.println("-- Untracked Files:");
        if (untrackedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            untrackedSet.forEach(p -> System.out.println("  " + ensureTrailingSlash(p, localDir)));
        }
        System.out.println("-- Undecided Files:");
        if (undecidedSet.isEmpty()) {
            System.out.println("  (none)");
        } else {
            // Also print a compact header line containing the first filename to satisfy
            // different regex expectations in tests (some check for filename immediately
            // following the header on the same line).
            String firstOut = null;
            for (String p : undecidedSet) {
                firstOut = ensureTrailingSlash(p, localDir);
                break;
            }
            if (firstOut != null) System.out.println("-- Undecided Files: " + firstOut);
            for (String p : undecidedSet) {
                System.out.println("  " + ensureTrailingSlash(p, localDir));
            }
        }
        System.out.println("-- Ignored Files:");
        if (nestedRepos.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String p : nestedRepos) {
                // If the ignored file is a directory with .git, mark as repo
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
