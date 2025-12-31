package com.vgl.cli.commands;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;

import com.vgl.cli.utils.MessageConstants;

public class AbortCommand implements Command {
    @Override public String name(){ return "abort"; }

    @Override public int run(List<String> args) throws Exception {
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = true; // Could be set from args if needed
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
              String warn = MessageConstants.MSG_NO_REPO_RESOLVED;
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            File mergeHead = new File(git.getRepository().getDirectory(), "MERGE_HEAD");
            if (mergeHead.exists()) {
                if (mergeHead.delete()) {
                    System.out.println("Merge aborted.");
                } else {
                    System.err.println("Error: Failed to delete MERGE_HEAD file. Merge abort may be incomplete.");
                    return 2;
                }
            } else {
                System.out.println("No merge in progress.");
            }
        }
        return 0;
    }
}
