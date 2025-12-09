package com.vgl.refactor;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal StatusService implementation using JGit Status as the primary source.
 * Does not attempt rename detection yet; renamed count will be zero.
 */
public class DefaultStatusService implements StatusService {
    @Override
    public StatusModel computeStatus(Git git, List<String> filters, boolean verbose, boolean veryVerbose) throws Exception {
        StatusModel model = new StatusModel();
        Status status = null;
        try {
            status = git.status().call();
        } catch (Exception e) {
            // If status fails, return empty model
            return model;
        }

        if (status == null) return model;

        model.modified = status.getModified().size() + status.getChanged().size();
        model.added = status.getAdded().size();
        model.removed = status.getRemoved().size() + status.getMissing().size();
        // Attempt to detect renames from the last commit and from working tree
        try {
            Repository repo = git.getRepository();
            // commit-derived renames (HEAD^ -> HEAD)
            Set<String> commitRenamedTargets = new HashSet<>();
            Set<String> commitRenameSources = new HashSet<>();
            try (RevWalk revWalk = new RevWalk(repo)) {
                ObjectReader reader = repo.newObjectReader();
                RevCommit head = revWalk.parseCommit(repo.resolve("HEAD"));
                if (head.getParentCount() > 0) {
                    RevCommit parent = revWalk.parseCommit(head.getParent(0));
                    CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                    oldTreeIter.reset(reader, parent.getTree());
                    CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                    newTreeIter.reset(reader, head.getTree());

                    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repo);
                        df.setDetectRenames(true);
                        List<DiffEntry> diffs = df.scan(oldTreeIter, newTreeIter);
                        for (DiffEntry d : diffs) {
                            if (d.getChangeType() == DiffEntry.ChangeType.RENAME) {
                                commitRenamedTargets.add(d.getNewPath());
                                commitRenameSources.add(d.getOldPath());
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}

            // working-tree renames: compare HEAD tree to working tree
            Map<String, String> workingRenames = new LinkedHashMap<>();
            try (ObjectReader reader = repo.newObjectReader()) {
                CanonicalTreeParser headTreeIter = new CanonicalTreeParser();
                try (RevWalk rw = new RevWalk(repo)) {
                    RevCommit head = rw.parseCommit(repo.resolve("HEAD"));
                    headTreeIter.reset(reader, head.getTree());
                } catch (Exception e) {
                    // no HEAD: skip working-tree rename detection
                    headTreeIter = null;
                }
                if (headTreeIter != null) {
                    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repo);
                        df.setDetectRenames(true);
                        FileTreeIterator workTreeIter = new FileTreeIterator(repo);
                        List<DiffEntry> diffs = df.scan(headTreeIter, workTreeIter);
                        for (DiffEntry d : diffs) {
                            if (d.getChangeType() == DiffEntry.ChangeType.RENAME) {
                                workingRenames.put(d.getOldPath(), d.getNewPath());
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}

            // Build displayed rename targets: commit targets minus those re-renamed in working tree, plus working targets
            Set<String> displayedTargets = new HashSet<>();
            if (!commitRenamedTargets.isEmpty()) {
                for (String t : commitRenamedTargets) {
                    if (!workingRenames.containsKey(t)) displayedTargets.add(t);
                }
            }
            if (!workingRenames.isEmpty()) displayedTargets.addAll(workingRenames.values());

            model.renamed = displayedTargets.size();
            model.renameTargets.addAll(displayedTargets);

            // Compute sources for adjustment
            Set<String> allSources = new HashSet<>();
            allSources.addAll(commitRenameSources);
            allSources.addAll(workingRenames.keySet());
            model.renameSources.addAll(allSources);

            // Adjust added/removed counts to avoid double counting rename targets/sources
            if (!displayedTargets.isEmpty()) {
                for (String t : displayedTargets) {
                    if (status.getAdded().contains(t)) model.added = Math.max(0, model.added - 1);
                }
            }
            if (!allSources.isEmpty()) {
                for (String s : allSources) {
                    if (status.getRemoved().contains(s) || status.getMissing().contains(s)) model.removed = Math.max(0, model.removed - 1);
                }
            }
        } catch (Exception ignore) {}

        model.tracked.addAll(status.getModified());
        model.tracked.addAll(status.getChanged());

        model.untracked.addAll(status.getUntracked());

        // undecided and ignored are left empty for now; callers can compute if needed
        model.ignored.addAll(status.getIgnoredNotInIndex());

        return model;
    }
}
