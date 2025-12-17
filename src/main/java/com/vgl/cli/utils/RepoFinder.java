package com.vgl.cli.utils;

import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.nio.file.Path;

public final class RepoFinder {
    private RepoFinder() {}

    /**
     * Find the git repository root directory starting at {@code start} and walking up.
     * Returns null if no git repository is found.
     */
    public static Path findRepoRoot(Path start) throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(start.toFile());
        File gitDir = builder.getGitDir();
        if (gitDir == null) return null;
        File workTree = gitDir.getParentFile();
        return workTree == null ? null : workTree.toPath();
    }
}
