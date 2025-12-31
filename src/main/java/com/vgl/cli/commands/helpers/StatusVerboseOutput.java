package com.vgl.cli.commands.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class StatusVerboseOutput {
    private StatusVerboseOutput() {}

    private static final int DEFAULT_LINE_WIDTH = 80;

    public static void printVeryVerbose(
        Set<String> tracked,
        Set<String> untracked,
        Set<String> ignored,
        String repoRoot,
        List<String> filters
    ) {
        printList("-- Tracked Files:", tracked, repoRoot, filters);
        printList("-- Untracked Files:", untracked, repoRoot, filters);
        printIgnored("-- Ignored Files:", ignored, repoRoot);
    }

    /**
     * Prints a file list subsection using a compact, ls-like horizontal layout.
     * Items are separated by two spaces and wrapped at a fixed width for stable tests.
     */
    public static void printCompactList(String header, Set<String> paths, String repoRoot, List<String> filters) {
        printList(header, paths, repoRoot, filters);
    }

    private static boolean matchesAnyFilter(String path, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (String f : filters) {
            if (f == null || f.isBlank()) {
                continue;
            }
            if (f.contains("*") || f.contains("?")) {
                String regex = f.replace(".", "\\.").replace("*", ".*").replace("?", ".");
                if (path.matches(regex)) {
                    return true;
                }
            } else {
                if (path.equals(f) || path.startsWith(f + "/") || path.contains("/" + f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void printList(String header, Set<String> paths, String repoRoot, List<String> filters) {
        if (header != null && !header.isBlank()) {
            System.out.println(header);
        }
        if (paths == null || paths.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        List<String> sorted = new ArrayList<>(paths);
        Collections.sort(sorted);
        List<String> display = new ArrayList<>();
        for (String p : sorted) {
            if (!matchesAnyFilter(p, filters)) {
                continue;
            }
            display.add(ensureTrailingSlash(p, repoRoot));
        }
        if (display.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        printWrappedEntries(display, DEFAULT_LINE_WIDTH);
    }

    private static void printIgnored(String header, Set<String> paths, String repoRoot) {
        System.out.println(header);
        if (paths == null || paths.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        List<String> sorted = new ArrayList<>(paths);
        Collections.sort(sorted);
        List<String> display = new ArrayList<>();
        for (String p : sorted) {
            if (".git".equals(p)) {
                display.add(".git (repo)");
                continue;
            }
            if (p.endsWith(" (repo)")) {
                display.add(ensureIgnoredRepoDecoration(p, repoRoot));
                continue;
            }
            File file = new File(repoRoot, p);
            if (file.isDirectory() && new File(file, ".git").exists()) {
                display.add((p.endsWith("/") ? p : p + "/") + " (repo)");
            } else {
                display.add(p);
            }
        }

        if (display.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        printWrappedEntries(display, DEFAULT_LINE_WIDTH);
    }

    private static String ensureIgnoredRepoDecoration(String entry, String repoRoot) {
        // Normalize repo decoration to ensure directories display with trailing '/'.
        if (entry == null) {
            return null;
        }
        if (!entry.endsWith(" (repo)")) {
            return entry;
        }
        String base = entry.substring(0, entry.length() - " (repo)".length());
        String withSlash = ensureTrailingSlash(base, repoRoot);
        return withSlash + " (repo)";
    }

    private static void printWrappedEntries(List<String> entries, int maxWidth) {
        String indent = "  ";
        String sep = "  ";

        StringBuilder line = new StringBuilder(indent);
        boolean first = true;

        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String addition = (first ? "" : sep) + entry;
            if (!first && line.length() + addition.length() > maxWidth) {
                System.out.println(line);
                line = new StringBuilder(indent);
                first = true;
                addition = entry;
            }

            if (!first) {
                line.append(sep);
            }
            line.append(entry);
            first = false;
        }

        if (line.length() > indent.length()) {
            System.out.println(line);
        } else {
            System.out.println(indent + "(none)");
        }
    }

    private static String ensureTrailingSlash(String path, String repoRoot) {
        if (path == null) {
            return null;
        }
        // Preserve repo decoration entries like "dir (repo)" at the caller.
        if (path.endsWith(" (repo)")) {
            return path;
        }
        File file = new File(repoRoot, path);
        if (file.isDirectory() && !path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }
}
