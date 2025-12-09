package com.vgl.refactor;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.nio.file.Path;
import java.util.Set;

/**
 * Core VGL repo API. Implementations should provide compute-only methods
 * that do not persist state, plus explicit persistence methods invoked
 * only by interactive commands.
 */
public interface VglRepoCore {
    /**
     * Compute undecided files from the working tree/status without persisting.
     */
    Set<String> computeUndecidedFiles(Git git, Status status) throws Exception;

    /**
     * Persist undecided files to the repo-level config (explicit action).
     */
    void saveUndecidedFiles(Path repoRoot, Set<String> undecided) throws Exception;
}
