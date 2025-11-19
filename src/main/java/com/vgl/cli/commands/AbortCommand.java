package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.util.List;

public class AbortCommand implements Command {
    @Override public String name(){ return "abort"; }

    @Override public int run(List<String> args) throws Exception {
        try (Git git = Utils.openGit()) {
            if (git == null) return 1;
            File mergeHead = new File(git.getRepository().getDirectory(), "MERGE_HEAD");
            if (mergeHead.exists()) {
                mergeHead.delete();
                System.out.println("Merge aborted.");
            } else {
                System.out.println("No merge in progress.");
            }
        }
        return 0;
    }
}
