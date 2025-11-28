package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import java.nio.file.Path;

/**
 * Default implementation of GitService using JGit.
 */
public class DefaultGitService implements GitService {
    
    @Override
    public Git openRepository(Path repoPath) throws Exception {
        return Git.open(repoPath.toFile());
    }
}
