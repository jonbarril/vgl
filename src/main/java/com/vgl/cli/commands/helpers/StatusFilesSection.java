package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Git;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class StatusFilesSection {
    /**
     * Prints the FILES section for status output, including summary and verbose subsections.
     *
     * @param git The JGit instance (may be null)
     * @param filesLabelPad The padded label (e.g. "FILES:   ")
     * @param filesToCommit Map of files to commit (from commits section)
     * @param undecided Set to populate with undecided files
     * @param tracked Set to populate with tracked files
     * @param untracked Set to populate with untracked files
     * @param ignored Set to populate with ignored files
     * @param verbose true if -v
     * @param veryVerbose true if -vv
     * @param maxLen The max path length for truncation
     */
    public static void printFilesSection(Git git, String filesLabelPad, Map<String, String> filesToCommit,
                                         Set<String> undecided, Set<String> tracked, Set<String> untracked, Set<String> ignored,
                                         boolean verbose, boolean veryVerbose, int maxLen) {
        //
        // The following sets will be built from filesToCommit to match VGL's model
        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        Set<String> renamed = new LinkedHashSet<>();
        java.util.Set<String> workingRenameTargets = new java.util.HashSet<>();
        if (git != null) {
            try {
                org.eclipse.jgit.api.Status status = git.status().call();
                if (status != null) {
                    com.vgl.cli.commands.helpers.StatusCommandHelpers.resolveFileCategories(status, git.getRepository(), tracked, untracked, undecided, ignored);
                    // Build summary sets from filesToCommit (after all filtering)
                    added.clear();
                    modified.clear();
                    removed.clear();
                    renamed.clear();
                    for (var e : filesToCommit.entrySet()) {
                        switch (e.getValue()) {
                            case "A" -> added.add(e.getKey());
                            case "M" -> modified.add(e.getKey());
                            case "D" -> removed.add(e.getKey());
                            case "R" -> renamed.add(e.getKey());
                        }
                    }
                    // Robustly handle working-tree renames: ensure targets are only in tracked, sources are removed from all
                    java.util.Map<String, String> workingRenames = com.vgl.cli.commands.helpers.StatusSyncFiles.computeWorkingRenames(git);
                    java.util.Set<String> workingRenameSources = new java.util.HashSet<>(workingRenames.keySet());
                    workingRenameTargets = new java.util.HashSet<>(workingRenames.values());
                    // Remove all sources from all sets
                    tracked.removeAll(workingRenameSources);
                    untracked.removeAll(workingRenameSources);
                    undecided.removeAll(workingRenameSources);
                    // Remove all targets from undecided and untracked
                    undecided.removeAll(workingRenameTargets);
                    untracked.removeAll(workingRenameTargets);
                    // Add all targets to tracked if not ignored
                    for (String target : workingRenameTargets) {
                        if (!ignored.contains(target)) {
                            tracked.add(target);
                        }
                    }
                    // Ensure targets of working renames are not in undecided
                    for (String target : workingRenameTargets) {
                        undecided.remove(target);
                    }
                }
            } catch (Exception ignore) {}
        }
        // Guarantee all working rename targets are in tracked before printing summary
        for (String target : workingRenameTargets) {
            if (!ignored.contains(target)) {
                if (!tracked.contains(target)) {
                    tracked.add(target);
                }
            }
        }
        // After overlaying working renames, rebuild summary sets from filesToCommit
        added.clear();
        modified.clear();
        removed.clear();
        renamed.clear();
        for (var e : filesToCommit.entrySet()) {
            switch (e.getValue()) {
                case "A" -> added.add(e.getKey());
                case "M" -> modified.add(e.getKey());
                case "D" -> removed.add(e.getKey());
                case "R" -> renamed.add(e.getKey());
            }
        }
        // Print summary counts for Added, Modified, Renamed, Deleted using the correct sets
        System.out.print(filesLabelPad);
        int filesPadLen = filesLabelPad.length();
        com.vgl.cli.commands.helpers.StatusFileSummary.printFileSummary(
            modified.size(), // numModified
            added.size(),    // numAdded
            removed.size(),  // numRemoved
            renamed.size(),  // numReplaced
            0, // numToMerge (not used)
            undecided, tracked, untracked, ignored,
            filesPadLen
        );
        // Print verbose/very-verbose file and commit subsections if requested
        if (verbose || veryVerbose) {
            System.out.println("-- Files to Commit:"); // No indentation for subsection header
            if (filesToCommit.isEmpty()) System.out.println("  (none)");
            else for (var e : filesToCommit.entrySet()) System.out.println("  " + e.getValue() + " " + e.getKey()); // Indented content
        }
        if (veryVerbose) {
            // Print detailed file lists
            com.vgl.cli.commands.helpers.StatusVerboseOutput.printVerbose(tracked, untracked, undecided, ignored, git != null ? git.getRepository().getWorkTree().getAbsolutePath() : ".", new java.util.ArrayList<>());
        }
    }
}
