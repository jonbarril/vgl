package com.vgl.cli.commands;

import java.util.Set;

public class StatusVerboseOutput {
        // Common section headers for status output
        public static final String HEADER_UNDECIDED = "  -- Undecided Files:";
        public static final String HEADER_TRACKED = "  -- Tracked Files:";
        public static final String HEADER_UNTRACKED = "  -- Untracked Files:";
        public static final String HEADER_IGNORED = "  -- Ignored Files:";
        public static final String NONE = "  (none)";
    public static void printVerbose(Set<String> trackedSet, Set<String> untrackedSet, Set<String> undecidedSet, Set<String> ignoredSet, String localDir, java.util.List<String> filters) {
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
        System.out.println(HEADER_UNDECIDED);
        if (undecidedSet == null || undecidedSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            // Do not print a duplicated preview line; just list entries under the subsection header
            boolean anyPrinted = false;
            for (String p : undecidedSet) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
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
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
        }

        System.out.println(HEADER_UNTRACKED);
        if (untrackedSet == null || untrackedSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            boolean anyPrinted = false;
            for (String p : untrackedSet) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + ensureTrailingSlash(p, localDir));
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println("  (none)");
        }

        System.out.println(HEADER_IGNORED);
        if (ignoredSet == null || ignoredSet.isEmpty()) {
            System.out.println(NONE);
        } else {
            boolean anyPrinted = false;
            java.util.List<String> sorted = new java.util.ArrayList<>(ignoredSet);
            java.util.Collections.sort(sorted);
            for (String p : sorted) {
                if (!matchesFilter.test(p)) continue;
                System.out.println("  " + p);
                anyPrinted = true;
            }
            if (!anyPrinted) System.out.println(NONE);
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
