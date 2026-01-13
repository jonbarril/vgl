package com.vgl.cli.commands.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class StatusVerboseOutput {
    private StatusVerboseOutput() {}

    private static final int DEFAULT_LINE_WIDTH = 80;
    private static final int DEFAULT_MIN_GROUP_SIZE = 3;
    private static final String REPO_PREFIX = "@ ";

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
     * Prints a file list subsection using a compact, ls-like layout:
     * - Root-level items shown first in horizontal columns.
     * - Directories with multiple items shown as a directory header followed by leaf names in columns.
     */
    public static void printCompactList(String header, Set<String> paths, String repoRoot, List<String> filters) {
        printList(header, paths, repoRoot, filters);
    }

    /**
     * Prints entries using the same compact wrapping, preserving the provided order.
     * Intended for lists that aren't simple file paths (e.g., "A path", "M path").
     */
    public static void printCompactEntries(String header, List<String> entries) {
        if (header != null && !header.isBlank()) {
            System.out.println(header);
        }
        if (entries == null || entries.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        List<String> display = new ArrayList<>();
        for (String e : entries) {
            if (e == null || e.isBlank()) {
                continue;
            }
            display.add(e);
        }
        if (display.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        printLsStyleColumnsGroupedByDir(display, DEFAULT_LINE_WIDTH, DEFAULT_MIN_GROUP_SIZE);
    }

    /**
     * Prints entries as a flat, wrapped list of columns (no directory grouping), using the same
     * spacing/wrapping rules as other status compact outputs.
     *
     * <p>This is intended for non-path lists like branch names.
     */
    public static void printWrappedColumns(List<String> entries, String indent) {
        String safeIndent = (indent != null) ? indent : "";
        if (entries == null || entries.isEmpty()) {
            System.out.println(safeIndent + "(none)");
            return;
        }

        List<String> display = new ArrayList<>();
        for (String e : entries) {
            if (e == null || e.isBlank()) {
                continue;
            }
            display.add(e);
        }

        if (display.isEmpty()) {
            System.out.println(safeIndent + "(none)");
            return;
        }

        printWrappedEntries(display, DEFAULT_LINE_WIDTH, safeIndent);
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

        printLsStyleColumnsGroupedByDir(display, DEFAULT_LINE_WIDTH, DEFAULT_MIN_GROUP_SIZE);
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
            File file = new File(repoRoot, p);

            boolean isNestedRepo = file.isDirectory() && !".git".equals(p) && new File(file, ".git").exists();
            String normalized = ensureTrailingSlash(p, repoRoot);
            if (isNestedRepo) {
                display.add(REPO_PREFIX + normalized);
            } else {
                display.add(normalized);
            }
        }

        if (display.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        // Ignored stays flat (git-like): do not expand directory contents.
        printWrappedEntries(display, DEFAULT_LINE_WIDTH, "  ");
    }

    private static void printLsStyleColumnsGroupedByDir(List<String> entries, int maxWidth, int minGroupSize) {
        if (entries == null || entries.isEmpty()) {
            System.out.println("  (none)");
            return;
        }

        // First pass: group candidates by directory.
        java.util.LinkedHashMap<String, java.util.List<ParsedEntry>> candidates = new java.util.LinkedHashMap<>();
        java.util.List<String> rootLike = new java.util.ArrayList<>();

        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            ParsedEntry p = ParsedEntry.parse(entry);

            // Do not expand directory entries (those ending in '/'); keep as-is.
            if (p.isDirectoryEntry) {
                rootLike.add(entry);
                continue;
            }

            if (p.dirKey.isEmpty()) {
                rootLike.add(entry);
                continue;
            }

            candidates.computeIfAbsent(p.dirKey, k -> new java.util.ArrayList<>()).add(p);
        }

        // Second pass: expand only larger groups; keep small groups inline as full paths.
        java.util.LinkedHashMap<String, java.util.List<String>> expanded = new java.util.LinkedHashMap<>();
        for (var e : candidates.entrySet()) {
            String dir = e.getKey();
            java.util.List<ParsedEntry> ps = e.getValue();
            if (ps == null || ps.isEmpty()) {
                continue;
            }
            if (ps.size() < Math.max(1, minGroupSize)) {
                for (ParsedEntry p : ps) {
                    rootLike.add(p.fullDisplay);
                }
            } else {
                java.util.List<String> leafs = new java.util.ArrayList<>();
                for (ParsedEntry p : ps) {
                    leafs.add(p.leafDisplay);
                }
                expanded.put(dir, leafs);
            }
        }

        // Root-like items first (this matches typical ls behavior: files first, then dir blocks).
        if (!rootLike.isEmpty()) {
            printWrappedEntries(rootLike, maxWidth, "  ");
        } else {
            // Keep stable output for empty sections.
            // If there are only expanded groups, we don't print a root line.
        }

        // Then each expanded directory block.
        for (var e : expanded.entrySet()) {
            System.out.println("  " + e.getKey() + "/");
            printWrappedEntries(e.getValue(), maxWidth, "    ");
        }
    }

    private static void printWrappedEntries(List<String> entries, int maxWidth, String indent) {
        if (entries == null || entries.isEmpty()) {
            System.out.println(indent + "(none)");
            return;
        }

        String sep = "   ";

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

    private static final class ParsedEntry {
        private final String dirKey;
        private final String leafDisplay;
        private final String fullDisplay;
        private final boolean isDirectoryEntry;

        private ParsedEntry(String dirKey, String leafDisplay, String fullDisplay, boolean isDirectoryEntry) {
            this.dirKey = (dirKey == null) ? "" : dirKey;
            this.leafDisplay = leafDisplay;
            this.fullDisplay = fullDisplay;
            this.isDirectoryEntry = isDirectoryEntry;
        }

        static ParsedEntry parse(String entry) {
            String s = entry;

            String statusPrefix = null;
            if (s.length() > 2 && s.charAt(1) == ' ') {
                statusPrefix = s.substring(0, 1);
                s = s.substring(2);
            }

            boolean repoDecorated = false;
            if (!s.isEmpty() && s.charAt(0) == '@') {
                repoDecorated = true;
                s = s.substring(1);
            }

            boolean isDirectoryEntry = s.endsWith("/");

            int slash = s.lastIndexOf('/');
            String dirKey;
            String leaf;
            if (slash < 0 || (isDirectoryEntry && slash == s.length() - 1)) {
                // No directory component (or this is a directory entry itself).
                dirKey = "";
                leaf = s;
            } else {
                dirKey = s.substring(0, slash);
                leaf = s.substring(slash + 1);
            }

            StringBuilder leafDisplay = new StringBuilder();
            if (statusPrefix != null) {
                leafDisplay.append(statusPrefix).append(' ');
            }
            if (repoDecorated) {
                leafDisplay.append('@');
            }
            leafDisplay.append(leaf);

            String fullDisplay = entry;
            return new ParsedEntry(dirKey, leafDisplay.toString(), fullDisplay, isDirectoryEntry);
        }
    }

    private static String ensureTrailingSlash(String path, String repoRoot) {
        if (path == null) {
            return null;
        }
        File file = new File(repoRoot, path);
        if (file.isDirectory() && !path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }
}
