package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

            if (!lb && !rb) lb = true; // default local when not specified
            if (rb && remoteUrl == null) {
                System.out.println("No remote connected.");
                return 1;
            }

            // Local branch: show working tree changes
            if (lb && !rb) {
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
                    
                    boolean foundAny = false;
                    for (DiffEntry entry : diffs) {
                        String path = entry.getNewPath();
                        if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                            path = entry.getOldPath();
                        }
                        
                        // Apply filters
                        if (!filters.isEmpty() && !matchesAnyFilter(path, filters)) {
                            continue;
                        }
                        
                        foundAny = true;
                        
                        // Show file header
                        String changeType = switch (entry.getChangeType()) {
                            case ADD -> "NEW FILE";
                            case DELETE -> "DELETED";
                            case MODIFY -> "MODIFIED";
                            case RENAME -> "RENAMED";
                            case COPY -> "COPIED";
                            default -> "CHANGED";
                        };
                        System.out.println("=== " + changeType + ": " + path + " ===");
                        
                        // Try to read and compare file contents directly for better text handling
                        try {
                            if (entry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                                // Get old content from HEAD tree by path
                                org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(
                                    git.getRepository(), path, headTreeId);
                                
                                String oldContent = "";
                                if (treeWalk != null) {
                                    org.eclipse.jgit.lib.ObjectId objectId = treeWalk.getObjectId(0);
                                    
                                    try (org.eclipse.jgit.lib.ObjectReader objReader = git.getRepository().newObjectReader()) {
                                        org.eclipse.jgit.lib.ObjectLoader loader = objReader.open(objectId);
                                        oldContent = new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                    }
                                    treeWalk.close();
                                }
                                
                                // Get new content from working tree
                                java.nio.file.Path workingFile = git.getRepository().getWorkTree().toPath().resolve(path);
                                byte[] newBytes = java.nio.file.Files.readAllBytes(workingFile);
                                String newContent = new String(newBytes, java.nio.charset.StandardCharsets.UTF_8);
                                
                                // Simple line-by-line diff
                                String[] oldLines = oldContent.split("\r?\n", -1);
                                String[] newLines = newContent.split("\r?\n", -1);
                                
                                // Show only changed lines with minimal context
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
                                    // Skip unchanged lines for cleaner output
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
                    
                    reader.close();
                }
                return 0;
            }

            // Remote branch path (basic placeholder)
            if (rb) {
                System.out.println("(remote diff) compare with origin/" + git.getRepository().getBranch());
                return 0;
            }
        }
        return 0;
    }
    
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
