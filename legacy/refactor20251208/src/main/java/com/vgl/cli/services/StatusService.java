package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;

import java.util.List;
import java.util.Set;

public interface StatusService {
    /**
     * Compute a structured status model for the repository.
     */
    StatusModel computeStatus(Git git, List<String> filters, boolean verbose, boolean veryVerbose) throws Exception;

    /**
     * Result object containing all status information.
     */
    public static class StatusResult {
        public final Set<String> modified;
        public final Set<String> added;
        public final Set<String> removed;
        public final Set<String> untracked;
        public final Set<String> missing;
        public final Set<String> allTracked;
        public final CommitInfo latestCommit;

        public StatusResult(Set<String> modified, Set<String> added, Set<String> removed,
                            Set<String> untracked, Set<String> missing, Set<String> allTracked,
                            CommitInfo latestCommit) {
            this.modified = modified;
            this.added = added;
            this.removed = removed;
            this.untracked = untracked;
            this.missing = missing;
            this.allTracked = allTracked;
            this.latestCommit = latestCommit;
        }

        public boolean hasChanges() {
            return !modified.isEmpty() || !added.isEmpty() || !removed.isEmpty()
                || !untracked.isEmpty() || !missing.isEmpty();
        }
    }

    /**
     * Commit information.
     */
    public static class CommitInfo {
        public final String shortHash;
        public final String message;
        public final java.util.Date date;

        public CommitInfo(String shortHash, String message, java.util.Date date) {
            this.shortHash = shortHash;
            this.message = message;
            this.date = date;
        }
    }
}
