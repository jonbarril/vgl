package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DiffCommand supports three comparison modes:
 * 1. No flags or -lb: working files vs local branch
 * 2. -rb only: working files vs remote branch
 * 3. Both -lb -rb: local branch vs remote branch
 */
public class DiffCommand implements Command {
    @Override public String name(){ return "diff"; }

    @Override public int run(List<String> args) throws Exception {
        boolean lb = args.contains("-lb");
        boolean rb = args.contains("-rb");
        List<String> filters = new ArrayList<>();
        for (String s : args) if (!s.equals("-lb") && !s.equals("-rb")) filters.add(s);

        try (Git git = Utils.findGitRepoOrWarn()) {
            if (git == null) {
                return 1;
            }
            String remoteUrl = git.getRepository().getConfig().getString("remote","origin","url");

            // Validate remote exists if -rb is used
            if (rb && remoteUrl == null) {
                System.out.println("No remote connected.");
                return 1;
            }

            // Determine comparison mode
            if (!lb && !rb) {
                lb = true; // default: working vs local
            }

            if (lb && rb) {
                // Compare local branch vs remote branch
                return compareLocalVsRemote(git, filters);
            } else if (rb) {
                // Compare working files vs remote branch
                return compareWorkingVsRemote(git, filters);
            } else {
                // Compare working files vs local branch
                return compareWorkingVsLocal(git, filters);
            }
        }
    }

    /**
     * Compare working files vs local branch (default mode)
     */
    private int compareWorkingVsLocal(Git git, List<String> filters) throws Exception {
                // Get the HEAD commit
                org.eclipse.jgit.lib.ObjectId headCommitId = git.getRepository().resolve("HEAD");
                
                if (headCommitId == null) {
                    // No commits yet - show status only
                    Status st = git.status().call();
                    Set<String> out = new LinkedHashSet<>();
                    st.getAdded().forEach(p -> out.add("ADD " + p + " (new file)"));
                    st.getUntracked().forEach(p -> out.add("ADD " + p + " (untracked)"));
                    
                    if (!filters.isEmpty()) {
                        out.removeIf(line -> {
                            String file = line.replaceFirst("^[A-Z]+\\s+","").replaceFirst("\\s+\\(.*\\)$", "");
                            return !matchesAnyFilter(file, filters);
                        });
                    }
                    out.forEach(System.out::println);
                    return 0;
                }
                
                // Use DiffFormatter to get actual diffs
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
                    formatter.setRepository(git.getRepository());
                    formatter.setContext(3); // 3 lines of context
                    formatter.setDetectRenames(true);
                    
                    // Compare HEAD with working tree
                    ObjectReader reader = git.getRepository().newObjectReader();
                    
                    // Parse HEAD commit to get tree
                    org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
                    org.eclipse.jgit.revwalk.RevCommit headCommit = revWalk.parseCommit(headCommitId);
                    org.eclipse.jgit.lib.ObjectId headTreeId = headCommit.getTree().getId();
                    revWalk.close();
                    
                    CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                    oldTreeIter.reset(reader, headTreeId);
                    
                    List<DiffEntry> diffs = formatter.scan(oldTreeIter, new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository()));
                    
                    // Delegate to shared display logic (handles working-tree diffs)
                    displayDiffs(git, diffs, headTreeId, null, filters, true);
                    reader.close();
                }
                return 0;
    }
    
    /**
     * Compare working files vs remote branch
     */
    private int compareWorkingVsRemote(Git git, List<String> filters) throws Exception {
        // Fetch to ensure we have latest remote refs
        System.out.println("Fetching from remote...");
        try {
            git.fetch().setRemote("origin").call();
        } catch (org.eclipse.jgit.api.errors.TransportException e) {
            if (!e.getMessage().contains("Nothing to fetch")) {
                throw e;
            }
            // Nothing to fetch is fine - we already have the latest refs
        }
        
        String currentBranch = git.getRepository().getBranch();
        org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + currentBranch);
        
        if (remoteHead == null) {
            System.out.println("Remote branch not found.");
            return 1;
        }
        
        try (DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream())) {
            formatter.setRepository(git.getRepository());
            formatter.setContext(3);
            formatter.setDetectRenames(true);
            
            ObjectReader reader = git.getRepository().newObjectReader();
            org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
            org.eclipse.jgit.revwalk.RevCommit remoteCommit = revWalk.parseCommit(remoteHead);
            org.eclipse.jgit.lib.ObjectId remoteTreeId = remoteCommit.getTree().getId();
            revWalk.close();
            
            CanonicalTreeParser remoteTreeIter = new CanonicalTreeParser();
            remoteTreeIter.reset(reader, remoteTreeId);
            
            List<DiffEntry> diffs = formatter.scan(remoteTreeIter, new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository()));
            
            displayDiffs(git, diffs, remoteTreeId, null, filters, true);
            
            reader.close();
        }
        return 0;
    }

    /**
     * Compare local branch vs remote branch
     */
    private int compareLocalVsRemote(Git git, List<String> filters) throws Exception {
        // Fetch to ensure we have latest remote refs
        System.out.println("Fetching from remote...");
        try {
            git.fetch().setRemote("origin").call();
        } catch (org.eclipse.jgit.api.errors.TransportException e) {
            if (!e.getMessage().contains("Nothing to fetch")) {
                throw e;
            }
            // Nothing to fetch is fine - we already have the latest refs
        }
        
        org.eclipse.jgit.lib.ObjectId localHead = git.getRepository().resolve("HEAD");
        String currentBranch = git.getRepository().getBranch();
        org.eclipse.jgit.lib.ObjectId remoteHead = git.getRepository().resolve("origin/" + currentBranch);
        
        if (localHead == null) {
            System.out.println("No local commits yet.");
            return 0;
        }
        if (remoteHead == null) {
            System.out.println("Remote branch not found.");
            return 1;
        }
        
        if (localHead.equals(remoteHead)) {
            System.out.println("(branches are identical)");
            return 0;
        }
        
        // Use DiffFormatter to compare the two trees
        try (DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream())) {
            formatter.setRepository(git.getRepository());
            formatter.setContext(3);
            formatter.setDetectRenames(true);
            
            ObjectReader reader = git.getRepository().newObjectReader();
            org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
            org.eclipse.jgit.revwalk.RevCommit localCommit = revWalk.parseCommit(localHead);
            org.eclipse.jgit.revwalk.RevCommit remoteCommit = revWalk.parseCommit(remoteHead);
            
            CanonicalTreeParser remoteTree = new CanonicalTreeParser();
            CanonicalTreeParser localTree = new CanonicalTreeParser();
            remoteTree.reset(reader, remoteCommit.getTree());
            localTree.reset(reader, localCommit.getTree());
            
            // Compare remote (old) to local (new)
            List<DiffEntry> diffs = formatter.scan(remoteTree, localTree);
            
            displayDiffs(git, diffs, remoteCommit.getTree().getId(), localCommit.getTree().getId(), filters, false);
            
            revWalk.close();
            reader.close();
        }
        
        return 0;
    }

    /**
     * Display diffs with file content comparison
     * @param git Git repository
     * @param diffs List of diff entries
     * @param oldTreeId Old tree (commit tree)
     * @param newTreeId New tree (commit tree or null for working tree)
     * @param filters File filters
     * @param useWorkingTree Whether to compare against working tree
     */
    private void displayDiffs(Git git, List<DiffEntry> diffs, org.eclipse.jgit.lib.ObjectId oldTreeId, org.eclipse.jgit.lib.ObjectId newTreeId, 
                              List<String> filters, boolean useWorkingTree) throws Exception {
        boolean foundAny = false;
        
        org.eclipse.jgit.lib.Repository repo = git.getRepository();
        Path repoRoot = repo.getWorkTree().toPath();
        for (DiffEntry entry : diffs) {
            String path = entry.getNewPath();
            if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                path = entry.getOldPath();
            }
            // Ignore git-ignored files in diff output
            if (Utils.isGitIgnored(repoRoot.resolve(path), repo)) continue;
            // Apply filters
            if (!filters.isEmpty() && !matchesAnyFilter(path, filters)) {
                continue;
            }
            foundAny = true;
            // Show file header
            String changeType;
            switch (entry.getChangeType()) {
                case ADD:
                    changeType = "NEW FILE";
                    break;
                case DELETE:
                    changeType = "DELETED";
                    break;
                case MODIFY:
                    changeType = "MODIFIED";
                    break;
                case RENAME:
                    changeType = "RENAMED";
                    break;
                case COPY:
                    changeType = "COPIED";
                                        break;
                                    default:
                                        changeType = "CHANGED";
                                        break;
                                }
                                System.out.println("=== " + changeType + ": " + path + " ===");
                                // Try to read and compare file contents
                                try {
                                    if (entry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                                        // Get old content from tree
                                        org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(
                                            git.getRepository(), path, oldTreeId);
                                        String oldContent = "";
                                        if (treeWalk != null) {
                                            org.eclipse.jgit.lib.ObjectId objectId = treeWalk.getObjectId(0);
                                            try (org.eclipse.jgit.lib.ObjectReader objReader = git.getRepository().newObjectReader()) {
                                                org.eclipse.jgit.lib.ObjectLoader loader = objReader.open(objectId);
                                                oldContent = new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                            }
                                            treeWalk.close();
                                        }
                                        // Get new content (from working tree or commit tree)
                                        String newContent;
                                        if (useWorkingTree) {
                                            java.nio.file.Path workingFile = git.getRepository().getWorkTree().toPath().resolve(path);
                                            byte[] newBytes = java.nio.file.Files.readAllBytes(workingFile);
                                            newContent = new String(newBytes, java.nio.charset.StandardCharsets.UTF_8);
                                        } else {
                                            // Get from new tree
                                            org.eclipse.jgit.treewalk.TreeWalk newTreeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(
                                                git.getRepository(), path, newTreeId);
                                            newContent = "";
                                            if (newTreeWalk != null) {
                                                org.eclipse.jgit.lib.ObjectId newObjectId = newTreeWalk.getObjectId(0);
                                                try (org.eclipse.jgit.lib.ObjectReader objReader = git.getRepository().newObjectReader()) {
                                                    org.eclipse.jgit.lib.ObjectLoader loader = objReader.open(newObjectId);
                                                    newContent = new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                                }
                                                newTreeWalk.close();
                                            }
                                        }
                                        // Simple line-by-line diff
                                        String[] oldLines = oldContent.split("\r?\n", -1);
                                        String[] newLines = newContent.split("\r?\n", -1);
                                        // Show only changed lines
                                        for (int i = 0; i < Math.max(oldLines.length, newLines.length); i++) {
                                            String oldLine = i < oldLines.length ? oldLines[i] : null;
                                            String newLine = i < newLines.length ? newLines[i] : null;
                                            if (oldLine == null && newLine != null) {
                                                System.out.println("  + " + newLine);
                                            } else if (oldLine != null && newLine == null) {
                                                System.out.println("  - " + oldLine);
                                            } else if (oldLine != null && newLine != null && !oldLine.equals(newLine)) {
                                                System.out.println("  - " + oldLine);
                                                System.out.println("  + " + newLine);
                                            }
                                        }
                                    } else {
                                        // For ADD/DELETE, show simple status
                                        if (entry.getChangeType() == DiffEntry.ChangeType.ADD) {
                                            System.out.println("  (new file)");
                                        } else if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                                            System.out.println("  (deleted)");
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error reading file content: " + e.getMessage());
                                }
                                System.out.println(); // blank line after each file
        }
        
        if (!foundAny) {
            System.out.println("(no changes)");
        }
                        }
    // End of displayDiffs
    
    private boolean matchesAnyFilter(String path, List<String> filters) {
        for (String filter : filters) {
            // Check if it's a commit ID - skip for file filtering
            if (filter.matches("[0-9a-f]{7,40}")) {
                continue;
            }
            // Support glob patterns
            if (filter.contains("*") || filter.contains("?")) {
                String regex = filter.replace(".", "\\.")
                                    .replace("*", ".*")
                                    .replace("?", ".");
                if (path.matches(regex)) {
                    return true;
                }
            } else {
                // Exact match or path contains
                if (path.equals(filter) || path.startsWith(filter + "/") || path.contains("/" + filter)) {
                    return true;
                }
            }
        }
        return false;
    }
}
