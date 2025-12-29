package com.vgl.cli.services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for querying repository status.
 * Separates business logic from CLI presentation.
 */
public class StatusService {
    
    private final GitService gitService;
    
    public StatusService(GitService gitService) {
        this.gitService = gitService;
    }
    
    /**
     * Get repository status information.
     */
    public StatusResult getStatus(Path repoPath, List<String> fileFilters) throws Exception {
        try (Git git = gitService.openRepository(repoPath)) {
            Status status = git.status().call();
            
            // Get commit info
            CommitInfo latestCommit = getLatestCommit(git);
            
            // Filter files if specified
            Set<String> modified = filterFiles(status.getModified(), fileFilters);
            Set<String> added = filterFiles(status.getAdded(), fileFilters);
            Set<String> removed = filterFiles(status.getRemoved(), fileFilters);
            Set<String> untracked = filterFiles(status.getUntracked(), fileFilters);
            Set<String> missing = filterFiles(status.getMissing(), fileFilters);
            
            // Get all tracked files for -vv
            Set<String> allTracked = getAllTrackedFiles(git);
            
            return new StatusResult(
                modified, added, removed, untracked, missing, allTracked, latestCommit
            );
        }
    }
    
    private CommitInfo getLatestCommit(Git git) throws Exception {
        ObjectId head = git.getRepository().resolve("HEAD");
        if (head == null) {
            return null;
        }
        
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(head);
            return new CommitInfo(
                commit.abbreviate(7).name(),
                commit.getFullMessage().trim(),
                new Date(commit.getCommitTime() * 1000L)
            );
        }
    }
    
    private Set<String> filterFiles(Set<String> files, List<String> filters) {
        if (filters.isEmpty()) {
            return files;
        }
        
        return files.stream()
            .filter(file -> matchesAnyFilter(file, filters))
            .collect(Collectors.toSet());
    }
    
    private boolean matchesAnyFilter(String file, List<String> filters) {
        for (String filter : filters) {
            if (filter.contains("*")) {
                // Glob pattern
                String regex = filter.replace(".", "\\.").replace("*", ".*");
                if (file.matches(regex)) {
                    return true;
                }
            } else {
                // Exact match
                if (file.equals(filter)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private Set<String> getAllTrackedFiles(Git git) throws Exception {
        // This is a placeholder - would need full implementation
        return new TreeSet<>();
    }
    
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
        public final Date date;
        
        public CommitInfo(String shortHash, String message, Date date) {
            this.shortHash = shortHash;
            this.message = message;
            this.date = date;
        }
    }
}
