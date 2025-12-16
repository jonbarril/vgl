package com.vgl.cli.commands;

import com.vgl.cli.utils.RepoResolver;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.util.List;

public class AbortCommand implements Command {
    @Override public String name(){ return "abort"; }

    @Override public int run(List<String> args) throws Exception {
        com.vgl.cli.RepoResolution repoRes = RepoResolver.resolveForCommand();
        if (repoRes.getGit() == null) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
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
