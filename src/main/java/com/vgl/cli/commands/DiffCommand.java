package com.vgl.cli.commands;

import java.util.List;

import org.eclipse.jgit.api.Git;

import com.vgl.cli.services.RepoResolution;

/**
 * DiffCommand supports three comparison modes:
 * 1. No flags or -lb: working files vs local branch
 * 2. -rb only: working files vs remote branch
 * 3. Both -lb -rb: local branch vs remote branch
 */
public class DiffCommand implements Command {
    @Override public String name(){ return "diff"; }

    @Override
    public int run(List<String> args) throws Exception {
        // Auto-stage all changes before diff (Gitless-style, optional for diff)
        Git git = null;
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = !args.contains("-y");
        RepoResolution resolution = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (resolution == null || resolution.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH) {
            String warn = (resolution != null && resolution.getMessage() != null) ? resolution.getMessage() : com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_RESOLVED;
            System.err.println(warn);
            return 1;
        }
        git = resolution.getGit();
        // Always diff HEAD vs working tree (ignore index)
        org.eclipse.jgit.lib.ObjectId head = git.getRepository().resolve("HEAD");
        if (head != null) {
            org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository());
            org.eclipse.jgit.revwalk.RevCommit headCommit = rw.parseCommit(head);
            org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader();
            org.eclipse.jgit.treewalk.CanonicalTreeParser oldTree = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
            oldTree.reset(reader, headCommit.getTree());
            org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository());
            try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(System.out)) {
                df.setRepository(git.getRepository());
                df.setDetectRenames(true);
                java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(oldTree, workingTreeIt);
                for (org.eclipse.jgit.diff.DiffEntry d : diffs) {
                    df.format(d);
                }
            }
            reader.close();
            rw.close();
        }
        return 0;
    }
}
