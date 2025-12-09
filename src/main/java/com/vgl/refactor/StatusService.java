package com.vgl.refactor;

import org.eclipse.jgit.api.Git;

import java.util.List;

public interface StatusService {
    /**
     * Compute a structured status model for the repository.
     */
    StatusModel computeStatus(Git git, List<String> filters, boolean verbose, boolean veryVerbose) throws Exception;
}
