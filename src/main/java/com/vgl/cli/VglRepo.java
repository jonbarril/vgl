
package com.vgl.cli;

import java.util.List;

import org.eclipse.jgit.api.Git;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/**
 * Represents a VGL repository with both git and config.
 * Combines Git instance with .vgl configuration.
 */
public class VglRepo implements Closeable {
            /**
             * Update the undecided file list in .vgl by scanning for untracked, non-ignored files.
             * This should be called before status, commit, or any command that needs to prompt about undecided files.
             *
             * @param git The Git instance for the repo
             * @param status The Status object for the current working tree
             * @throws IOException if config cannot be saved
             */
            public void updateUndecidedFilesFromWorkingTree(org.eclipse.jgit.api.Git git, org.eclipse.jgit.api.Status status) throws IOException {
                Set<String> untracked = status.getUntracked();
                Set<String> ignored = new java.util.HashSet<>();
                try {
                    org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository());
                    org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository());
                    treeWalk.addTree(workingTreeIt);
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        org.eclipse.jgit.treewalk.WorkingTreeIterator workingTreeIterator = (org.eclipse.jgit.treewalk.WorkingTreeIterator) treeWalk.getTree(0, org.eclipse.jgit.treewalk.WorkingTreeIterator.class);
                        String path = treeWalk.getPathString();
                        if (workingTreeIterator != null && workingTreeIterator.isEntryIgnored()) {
                            ignored.add(path);
                        }
                    }
                    treeWalk.close();
                } catch (Exception e) {
                    // Ignore
                }
                // Only keep untracked files that are not ignored
                java.util.List<String> undecided = new java.util.ArrayList<>();
                // Compute working-tree renames so we don't treat renamed tracked files as undecided
                java.util.Map<String,String> workingRenames = new java.util.HashMap<>();
                try {
                    workingRenames = com.vgl.cli.commands.status.StatusSyncFiles.computeWorkingRenames(git);
                } catch (Exception ignoredEx) {}
                // Also compute commit-derived rename targets (fallback detection) so committed renames
                // are not considered undecided either.
                java.util.Set<String> commitRenameTargets = new java.util.HashSet<>();
                try {
                    java.util.Set<String> cr = com.vgl.cli.commands.status.StatusSyncFiles.computeCommitRenamedSet(git, status, "", "main");
                    if (cr != null) commitRenameTargets.addAll(cr);
                } catch (Exception ignoredEx) {}

                for (String f : untracked) {
                    // Normalize path separators to '/' for robust comparisons
                    String fn = f.replace('\\', '/');
                    // Never include the VGL config file itself in the undecided list
                    if (".vgl".equals(fn)) continue;
                    if (ignored.contains(fn)) continue;
                    // If this untracked file is the target of a detected rename from a tracked path,
                    // either in the working tree or in recent commits, skip adding it to undecided so it
                    // remains effectively tracked.
                    boolean isRenameTarget = false;
                    if (workingRenames != null && !workingRenames.isEmpty()) {
                        for (String v : workingRenames.values()) {
                            if (v != null && v.replace('\\','/').equals(fn)) { isRenameTarget = true; break; }
                        }
                    }
                    if (isRenameTarget) continue;
                    if (commitRenameTargets.contains(fn)) continue;
                    undecided.add(fn);
                }
                // Remove any entries that are nested repositories (they should be considered ignored)
                try {
                    java.util.Set<String> nested = Utils.listNestedRepos(repoRoot);
                    if (nested != null && !nested.isEmpty()) {
                        java.util.List<String> filtered = new java.util.ArrayList<>();
                        for (String u : undecided) {
                            boolean insideNested = false;
                            for (String n : nested) {
                                if (u.equals(n) || u.startsWith(n + "/")) { insideNested = true; break; }
                            }
                            if (!insideNested) filtered.add(u);
                        }
                        undecided = filtered;
                    }
                } catch (Exception ignore) {}
                setUndecidedFiles(undecided);
                saveConfig();
            }

            /**
             * Fallback update of undecided files when JGit Status cannot be obtained.
             * This scans the working tree for non-ignored files and treats them as undecided.
             * This is a best-effort fallback used when the repo has no commits or status fails.
             */
            public void updateUndecidedFilesFromWorkingTree(org.eclipse.jgit.api.Git git) throws IOException {
                java.util.List<String> undecided = new java.util.ArrayList<>();
                try {
                    org.eclipse.jgit.lib.Repository repo = git.getRepository();
                    java.util.Set<String> nonIgnored = Utils.listNonIgnoredFiles(repoRoot, repo);
                    for (String f : nonIgnored) {
                        if (".vgl".equals(f)) continue;
                        undecided.add(f);
                    }
                } catch (Exception e) {
                    // Best-effort: ignore failures here
                }
                // Filter out nested repositories from undecided
                try {
                    java.util.Set<String> nested = Utils.listNestedRepos(repoRoot);
                    if (nested != null && !nested.isEmpty()) {
                        java.util.List<String> filtered = new java.util.ArrayList<>();
                        for (String u : undecided) {
                            boolean insideNested = false;
                            for (String n : nested) {
                                if (u.equals(n) || u.startsWith(n + "/")) { insideNested = true; break; }
                            }
                            if (!insideNested) filtered.add(u);
                        }
                        undecided = filtered;
                    }
                } catch (Exception ignore) {}
                setUndecidedFiles(undecided);
                saveConfig();
            }
        private static final String UNDECIDED_KEY = "undecided.files";
    private final Git git;
    private final Path repoRoot;
    private final Properties config;
    
    /**
     * Create a VglRepo instance.
     * @param git The Git instance (must not be null)
     * @param config The loaded configuration (may be empty if no .vgl file)
     */
    public VglRepo(Git git, Properties config) {
        if (git == null) {
            throw new IllegalArgumentException("Git instance cannot be null");
        }
        this.git = git;
        this.repoRoot = git.getRepository().getWorkTree().toPath();
        this.config = config != null ? config : new Properties();
    }
    
    public Git getGit() {
        return git;
    }
    
    public Path getRepoRoot() {
        return repoRoot;
    }
    
    public Properties getConfig() {
        return config;
    }

    /**
     * Get the list of undecided files from the .vgl config.
     */
    public List<String> getUndecidedFiles() {
        String val = config.getProperty(UNDECIDED_KEY, "");
        if (val.isEmpty()) return new java.util.ArrayList<>();
        String[] parts = val.split(",");
        List<String> files = new java.util.ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) files.add(trimmed);
        }
        return files;
    }

    /**
     * Set the list of undecided files in the .vgl config.
     */
    public void setUndecidedFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            config.remove(UNDECIDED_KEY);
        } else {
            config.setProperty(UNDECIDED_KEY, String.join(",", files));
        }
    }

    /**
     * Save the .vgl config file to disk.
     */
    public void saveConfig() throws IOException {
        Path vglFile = repoRoot.resolve(".vgl");
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(vglFile)) {
            config.store(out, "VGL Configuration");
        }
    }
    
    /**
     * Get a configuration value with a default.
     */
    public String getConfigOrDefault(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
    
    @Override
    public void close() throws IOException {
        if (git != null) {
            git.close();
        }
    }
    
    /**
     * Load .vgl config file from the repository root.
     * @param repoRoot The root directory of the git repository
     * @return Loaded properties, or empty properties if no .vgl file
     */
    static Properties loadConfig(Path repoRoot) {
        Properties config = new Properties();
        
        if (repoRoot == null) {
            return config;
        }
        
        Path vglFile = repoRoot.resolve(".vgl");
        if (!Files.exists(vglFile)) {
            return config;
        }
        
        // Check if .git still exists (orphaned .vgl check)
        if (!Files.exists(repoRoot.resolve(".git"))) {
            System.err.println("Warning: Found .vgl but no .git directory.");
            System.err.println("The .git repository may have been deleted or moved.");
            return config;
        }
        
        // Load the config
        try (InputStream in = Files.newInputStream(vglFile)) {
            config.load(in);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load .vgl configuration file.");
        }
        
        return config;
    }
}
