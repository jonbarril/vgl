package com.vgl.cli.utils;

import org.eclipse.jgit.lib.Repository;

import java.nio.file.Path;

public final class RepoUtils {
    private RepoUtils() {}

    public static String normalizeToRepoPath(Path repoRoot, Path file) {
        Path rel = repoRoot.relativize(file);
        String s = rel.toString().replace('\\', '/');
        return s;
    }

    public static boolean isGitIgnored(Path path, Repository repository) {
        // Lightweight placeholder: callers should use JGit ignore checks when needed.
        return false;
    }
}
