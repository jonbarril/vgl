package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import java.nio.file.Path;

/**
 * Interface for Git operations to enable mocking in tests.
 */
public interface GitService {
    
    /**
     * Open a Git repository at the specified path.
     */
    Git openRepository(Path repoPath) throws Exception;
}
