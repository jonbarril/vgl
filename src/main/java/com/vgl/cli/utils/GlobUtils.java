package com.vgl.cli.utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GlobUtils {
    private GlobUtils() {}

    /** Returns true if {@code repoRelativePath} matches any of {@code globs}. */
    public static boolean matchesAny(String repoRelativePath, List<String> globs) {
        if (repoRelativePath == null || repoRelativePath.isBlank()) {
            return false;
        }
        if (globs == null || globs.isEmpty()) {
            return false;
        }

        String p = repoRelativePath.replace('\\', '/');
        for (String g : globs) {
            if (g == null || g.isBlank()) {
                continue;
            }
            String trimmed = g.trim();
            if (trimmed.equals("*") || trimmed.equals(".")) {
                return true;
            }

            // Match literal paths and literal directories.
            if (!hasWildcard(trimmed)) {
                String lit = trimmed.replace('\\', '/');
                // Convenience: a bare filename (no path separators) matches any file with that basename.
                if (!lit.contains("/")) {
                    if (p.equals(lit) || p.endsWith("/" + lit)) {
                        return true;
                    }
                }
                if (p.equals(lit) || p.startsWith(lit + "/")) {
                    return true;
                }
            }

            String regex = globToRegex(trimmed);
            if (p.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Expands glob-ish patterns into repo-root-relative file paths (with '/' separators).
     * Expansion is bounded to {@code repoRoot} and excludes nested git repositories.
     */
    public static List<String> expandGlobsToFiles(List<String> globs, Path repoRoot) throws IOException {
        if (repoRoot == null) {
            return Collections.emptyList();
        }
        if (globs == null || globs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> patterns = new ArrayList<>();
        for (String g : globs) {
            if (g == null || g.isBlank()) {
                continue;
            }
            String trimmed = g.trim();
            if (trimmed.equals("*")) {
                patterns.add("**");
            } else if (trimmed.equals(".")) {
                patterns.add("**");
            } else if (!hasWildcard(trimmed) && !trimmed.contains("/") && !trimmed.contains("\\")) {
                // Convenience: allow passing just a filename to match anywhere in the repo.
                patterns.add(trimmed);
                patterns.add("**/" + trimmed);
            } else {
                patterns.add(trimmed);
            }
        }
        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }

        Path root = repoRoot.toAbsolutePath().normalize();
        Set<String> nested = GitUtils.listNestedRepos(root);

        Set<String> out = new LinkedHashSet<>();
        for (String pattern : patterns) {
            // If the pattern is a literal directory, expand recursively.
            if (!hasWildcard(pattern)) {
                Path abs = root.resolve(pattern).normalize();
                if (Files.isDirectory(abs) && abs.startsWith(root)) {
                    collectFilesUnderDir(out, root, abs, nested);
                    continue;
                }
            }

            String regex = globToRegex(pattern);
            // Walk the repo and match.
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = root.relativize(dir);
                    String relStr = rel.toString().replace('\\', '/');
                    if (".git".equals(relStr) || relStr.endsWith("/.git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // Skip nested repo subtrees.
                    for (String n : nested) {
                        if (relStr.equals(n) || relStr.startsWith(n + "/")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path rel = root.relativize(file);
                    String relStr = rel.toString().replace('\\', '/');
                    if (relStr.equals(".vgl") || relStr.equals(".gitignore")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (relStr.matches(regex)) {
                        out.add(relStr);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        List<String> sorted = new ArrayList<>(out);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Resolve globs against the repo root and print a short report to `out`.
     * Returns the sorted list of matched repo-relative paths (or an empty list).
     */
    public static List<String> resolveGlobs(List<String> globs, Path repoRoot, java.io.PrintStream out) throws IOException {
        List<String> resolved = expandGlobsToFiles(globs, repoRoot);
        if (resolved == null || resolved.isEmpty()) {
            if (out != null) {
                out.println("No files matched globs: " + String.join(", ", globs == null ? List.of() : globs));
            }
            return List.of();
        }
        if (out != null) {
            out.println("Globs resolved to " + resolved.size() + " file(s)");
        }
        return resolved;
    }

    private static void collectFilesUnderDir(Set<String> out, Path repoRoot, Path dir, Set<String> nested) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (d.equals(dir)) {
                    return FileVisitResult.CONTINUE;
                }
                Path rel = repoRoot.relativize(d);
                String relStr = rel.toString().replace('\\', '/');
                if (".git".equals(relStr) || relStr.endsWith("/.git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                for (String n : nested) {
                    if (relStr.equals(n) || relStr.startsWith(n + "/")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                Path rel = repoRoot.relativize(file);
                String relStr = rel.toString().replace('\\', '/');
                if (relStr.equals(".vgl") || relStr.equals(".gitignore")) {
                    return FileVisitResult.CONTINUE;
                }
                out.add(relStr);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean hasWildcard(String pattern) {
        return pattern.contains("*") || pattern.contains("?");
    }

    /**
     * Very small glob-to-regex converter for our CLI patterns.
     * Supports *, ?, and ** (match any segments).
     */
    private static String globToRegex(String glob) {
        String g = glob.replace('\\', '/');

        StringBuilder sb = new StringBuilder();
        sb.append("^");

        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*') {
                boolean isDouble = (i + 1 < g.length() && g.charAt(i + 1) == '*');
                if (isDouble) {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                if (".[]{}()+-^$|\\".indexOf(c) >= 0) {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }

        sb.append("$");
        return sb.toString();
    }
}
