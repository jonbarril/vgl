package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import com.vgl.cli.VglCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoFilepatternException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.List;

public class TrackCommand implements Command {
    @Override public String name(){ return "track"; }

    @Override public int run(List<String> args) throws Exception {
        System.out.println("[vgl.debug:FORCE] Args: " + args);
        System.out.flush();
        if (args.isEmpty()) {
            System.out.println("Usage: vgl track <glob...> | -all");
            return 1;
        }

        VglCli vgl = new VglCli();
        String localDir = vgl.getLocalDir();
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();

        if (!vgl.isConfigurable()) {
            System.out.println(Utils.MSG_NO_REPO_WARNING_PREFIX + dir);
            return 1;
        }

        // Load undecided files from .vgl if -all is specified
        boolean useAll = args.size() == 1 && args.get(0).equals("-all");
        List<String> filesToTrack;
        com.vgl.cli.VglRepo vglRepo = com.vgl.cli.Utils.findVglRepo(dir);
        if (vglRepo != null) {
            try (Git git = Git.open(dir.toFile())) {
                // Only update undecided files before main logic
                org.eclipse.jgit.lib.Repository repo = git.getRepository();
                try {
                    if (Utils.hasCommits(repo)) {
                        org.eclipse.jgit.api.Status status = git.status().call();
                        vglRepo.updateUndecidedFilesFromWorkingTree(git, status);
                    } else {
                        vglRepo.updateUndecidedFilesFromWorkingTree(git);
                    }
                } catch (Exception e) {
                    if (Boolean.getBoolean("vgl.debug")) {
                        System.err.println("[vgl.debug] TrackCommand: status read failed: " + e.getMessage());
                    }
                }
            }
        }
        if (useAll) {
            if (vglRepo == null) {
                System.out.println("No .vgl repo found for undecided files.");
                return 1;
            }
            // When using -all, exclude any undecided files that are inside nested repositories.
            filesToTrack = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
            try {
                java.util.Set<String> nested = Utils.listNestedRepos(dir);
                if (nested != null && !nested.isEmpty()) {
                    java.util.List<String> filtered = new java.util.ArrayList<>();
                    for (String f : filesToTrack) {
                        boolean insideNested = false;
                        for (String n : nested) {
                            if (f.equals(n) || f.startsWith(n + "/")) { insideNested = true; break; }
                        }
                        if (!insideNested) filtered.add(f);
                    }
                    filesToTrack = filtered;
                }
            } catch (Exception ignored) {}
            if (filesToTrack.isEmpty()) {
                System.out.println("No undecided files to track.");
                return 0;
            }
        } else {
            try (Git git = Git.open(dir.toFile())) {
                filesToTrack = Utils.expandGlobsToFiles(args, dir, git.getRepository());
            }
        }

        // Detect if any of the provided globs matched nested repositories (they are ignored)
        java.util.List<String> nestedMatches = new java.util.ArrayList<>();
        if (!useAll) {
            for (String g : args) {
                try {
                    final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
                    try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(dir)) {
                        s.filter(java.nio.file.Files::isDirectory).forEach(d -> {
                            try {
                                Path rel = dir.relativize(d);
                                if (m.matches(rel) && java.nio.file.Files.exists(d.resolve(".git"))) {
                                    String r = rel.toString().replace('\\','/') + "/";
                                    nestedMatches.add(r);
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            }
        }

        if (filesToTrack.isEmpty()) {
            System.out.println("No matching files to track.");
            return 1;
        }

        try (Git git = Git.open(dir.toFile())) {
            org.eclipse.jgit.lib.Repository repo = git.getRepository();
            // Compute set of files already tracked in HEAD (if any) so we don't attempt to re-add them
            java.util.Set<String> trackedBefore = new java.util.LinkedHashSet<>();
            final boolean[] jgitOkArr = new boolean[]{false};
            try {
                if (Utils.hasCommits(repo)) {
                    org.eclipse.jgit.lib.ObjectId headTree = repo.resolve("HEAD^{tree}");
                    if (headTree != null) {
                        org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repo);
                        tw.addTree(headTree);
                        tw.setRecursive(true);
                        while (tw.next()) {
                            trackedBefore.add(tw.getPathString().replace('\\','/'));
                        }
                        tw.close();
                        jgitOkArr[0] = true;
                    }
                }
            } catch (Exception ignored) {}
            // Read status if possible so we can detect modified tracked files
            final org.eclipse.jgit.api.Status[] preStatusArr = new org.eclipse.jgit.api.Status[1];
            try {
                preStatusArr[0] = git.status().call();
                jgitOkArr[0] = true;
            } catch (Exception ignored) {}

            List<String> filteredFiles = new java.util.ArrayList<>();
            // Reject attempts to track nested repos explicitly
            java.util.List<String> nestedRequests = new java.util.ArrayList<>();
            for (String p : filesToTrack) {
                Path fp = dir.resolve(p).toAbsolutePath().normalize();
                if (Utils.isInsideNestedRepo(dir, fp)) {
                    nestedRequests.add(p);
                }
            }
            if (!nestedRequests.isEmpty()) {
                System.out.println("Error: Cannot track nested repository paths: " + String.join(" ", nestedRequests));
                System.out.println("Remove or convert nested repositories to submodules before tracking their files.");
                return 1;
            }

            for (String p : filesToTrack) {
                Path filePath = dir.resolve(p);
                try {
                    if (Files.isRegularFile(filePath)) {
                        if (!Utils.isGitIgnored(filePath, repo)) {
                            if (!trackedBefore.contains(p)) {
                                filteredFiles.add(p);
                            } else {
                                // File already tracked; only add if it has modifications in working tree
                                boolean modified = false;
                                if (preStatusArr[0] != null) {
                                    modified = preStatusArr[0].getModified().contains(p) || preStatusArr[0].getChanged().contains(p) || preStatusArr[0].getRemoved().contains(p) || preStatusArr[0].getMissing().contains(p) || preStatusArr[0].getAdded().contains(p);
                                }
                                if (!jgitOkArr[0]) {
                                    // Fallback: check native git ls-files --stage for tracked status
                                    try {
                                        Process proc = new ProcessBuilder("git", "ls-files", "--stage").directory(dir.toFile()).start();
                                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            if (line.endsWith(p)) {
                                                modified = true;
                                                break;
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                                if (modified) filteredFiles.add(p);
                            }
                        }
                    } else if (Files.isDirectory(filePath)) {
                        // Expand directory to regular files under it (skip any nested repos found under the directory)
                        try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(filePath)) {
                            s.filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                                try {
                                    if (Utils.isInsideNestedRepo(dir, f)) return; // skip files inside nested repos
                                    Path rel = dir.relativize(f);
                                    String rels = rel.toString().replace('\\','/');
                                    if (!Utils.isGitIgnored(f, repo)) {
                                        if (!trackedBefore.contains(rels)) {
                                            filteredFiles.add(rels);
                                        } else {
                                            boolean modified = false;
                                            if (preStatusArr[0] != null) {
                                                modified = preStatusArr[0].getModified().contains(rels) || preStatusArr[0].getChanged().contains(rels) || preStatusArr[0].getRemoved().contains(rels) || preStatusArr[0].getMissing().contains(rels) || preStatusArr[0].getAdded().contains(rels);
                                            }
                                            if (!jgitOkArr[0]) {
                                                try {
                                                    Process proc = new ProcessBuilder("git", "ls-files", "--stage").directory(dir.toFile()).start();
                                                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
                                                    String line;
                                                    while ((line = reader.readLine()) != null) {
                                                        if (line.endsWith(rels)) {
                                                            modified = true;
                                                            break;
                                                        }
                                                    }
                                                } catch (Exception ignored) {}
                                            }
                                            if (modified) filteredFiles.add(rels);
                                        }
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                    } else {
                        // Path may be a glob match that doesn't exist as file/dir; leave it for git to handle
                        if (!Utils.isGitIgnored(filePath, repo)) {
                            if (!trackedBefore.contains(p)) filteredFiles.add(p);
                            else {
                                boolean modified = false;
                                if (preStatusArr[0] != null) {
                                    modified = preStatusArr[0].getModified().contains(p) || preStatusArr[0].getChanged().contains(p) || preStatusArr[0].getRemoved().contains(p) || preStatusArr[0].getMissing().contains(p) || preStatusArr[0].getAdded().contains(p);
                                }
                                if (!jgitOkArr[0]) {
                                    try {
                                        Process proc = new ProcessBuilder("git", "ls-files", "--stage").directory(dir.toFile()).start();
                                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            if (line.endsWith(p)) {
                                                modified = true;
                                                break;
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                                if (modified) filteredFiles.add(p);
                            }
                        }
                    }
                } catch (Exception e) {
                    // best-effort: include pattern if not ignored
                    if (!Utils.isGitIgnored(filePath, repo)) filteredFiles.add(p);
                }
            }
            if (filteredFiles.isEmpty()) {
                System.out.println("All matching files are ignored by git.");
                return 1;
            }
            var addc = git.add();
            for (String p : filteredFiles) {
                addc.addFilepattern(p);
            }
            try {
                addc.call();

                // Verify which files were actually staged; warn about any that remain untracked
                java.util.List<String> actuallyTracked = new java.util.ArrayList<>();
                java.util.List<String> failed = new java.util.ArrayList<>();
                try {
                    org.eclipse.jgit.api.Status post = git.status().call();
                    java.util.Set<String> added = post.getAdded();
                    java.util.Set<String> changed = post.getChanged();
                    java.util.Set<String> removed = post.getRemoved();
                    java.util.Set<String> missing = post.getMissing();
                    java.util.Set<String> untracked = post.getUntracked();

                    for (String p : filteredFiles) {
                        boolean isStaged = false;
                        // Consider a file successfully staged if it appears in the added/changed sets
                        if (added.contains(p) || changed.contains(p) || removed.contains(p) || missing.contains(p)) {
                            isStaged = true;
                        } else if (untracked.contains(p)) {
                            isStaged = false;
                        } else {
                            // Status didn't explicitly report it; check the index (DirCache) to be sure.
                            try {
                                org.eclipse.jgit.dircache.DirCache dc = repo.readDirCache();
                                if (dc.getEntry(p) != null) isStaged = true;
                            } catch (Exception e) {
                                // If we cannot read the index, fall back to conservative assumption
                                if (Boolean.getBoolean("vgl.debug")) {
                                    System.err.println("[vgl.debug] TrackCommand: DirCache read failed: " + e.getMessage());
                                }
                                isStaged = true;
                            }
                        }

                        if (isStaged) {
                            // Use filteredFiles entries directly for actuallyTracked
                            actuallyTracked.add(p);
                        } else failed.add(p);
                    }
                } catch (Exception e) {
                    // If status cannot be read (unborn HEAD, repository odd state), log debug and conservatively
                    // assume the files we attempted to add are tracked. This keeps behavior consistent with
                    // the command output while avoiding false failure messages.
                    if (Boolean.getBoolean("vgl.debug")) {
                        System.err.println("[vgl.debug] TrackCommand: post-add status read failed: " + e.getMessage());
                    }
                    actuallyTracked.addAll(filteredFiles);
                }

                if (!actuallyTracked.isEmpty()) {
                    // If the user requested '.' specifically, echo that instead of listing every file
                    if (args.size() == 1 && ".".equals(args.get(0))) {
                        System.out.println("Tracking: .");
                    } else {
                        System.out.println("Tracking: " + String.join(" ", actuallyTracked));
                    }
                }
                if (!failed.isEmpty()) {
                    System.out.println("Warning: The following files could not be tracked: " + String.join(" ", failed));
                }

                // If any glob patterns matched nested repos, warn user
                if (!nestedMatches.isEmpty()) {
                    // Deduplicate
                    java.util.Set<String> uniq = new java.util.LinkedHashSet<>(nestedMatches);
                    System.out.println("Warning: The following paths are nested repositories and were ignored: " + String.join(" ", uniq));
                }

                // Remove tracked files from undecided in .vgl
                if (vglRepo != null) {
                    List<String> undecided = new java.util.ArrayList<>(vglRepo.getUndecidedFiles());
                    List<String> newUndecided = new java.util.ArrayList<>();
                    try (Git gitStatus = Git.open(vglRepo.getRepoRoot().toFile())) {
                        org.eclipse.jgit.lib.Repository statusRepo = gitStatus.getRepository();
                        if (Utils.hasCommits(statusRepo)) {
                            org.eclipse.jgit.api.Status status = gitStatus.status().call();
                            java.util.Set<String> staged = new java.util.HashSet<>();
                            staged.addAll(status.getAdded());
                            staged.addAll(status.getChanged());
                            staged.addAll(status.getRemoved());
                            staged.addAll(status.getMissing());
                            staged.addAll(status.getModified());
                            System.out.println("[vgl.debug:FORCE] Undecided before: " + undecided);
                            System.out.println("[vgl.debug:FORCE] Tracked set: " + staged);
                            for (String u : undecided) {
                                String uNorm = u.replace('\\','/');
                                if (!staged.contains(uNorm)) {
                                    newUndecided.add(u);
                                }
                            }
                            System.out.println("[vgl.debug:FORCE] Undecided after: " + newUndecided);
                            System.out.flush();
                        } else {
                            // No commits: skip status logic and debug output, keep undecided unchanged
                            newUndecided.addAll(undecided);
                        }
                    } catch (Exception e) {
                        // Defensive: if status fails, keep undecided unchanged
                        newUndecided.addAll(undecided);
                    }
                    vglRepo.setUndecidedFiles(newUndecided);
                    vglRepo.saveConfig();
                }
            } catch (NoFilepatternException ex) {
                System.out.println("No matching files to track.");
            }
        }
        return 0;
    }
}

// ...rest of the TrackCommand class and run() method as previously read...
