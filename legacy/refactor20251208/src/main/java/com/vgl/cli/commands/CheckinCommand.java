package com.vgl.cli.commands;

import com.vgl.cli.services.RepoResolution;
import org.eclipse.jgit.api.Git;

import com.vgl.cli.utils.MessageConstants;

import java.util.List;

public class CheckinCommand implements Command {
    @Override public String name(){ return "checkin"; }

    @Override public int run(List<String> args) throws Exception {
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = !args.contains("-y");
        RepoResolution resolution = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (resolution == null || resolution.getKind() != RepoResolution.ResolutionKind.FOUND_BOTH) {
            String warn = (resolution != null && resolution.getMessage() != null) ? resolution.getMessage() : MessageConstants.MSG_CHECKIN_ERR_NO_REPO;
            System.err.println(warn);
            return 1;
        }

        boolean draft = args.contains("-draft");
        boolean fin = args.contains("-final");
        // Accept checkin if any files are specified, for test compatibility
        List<String> files = args.stream().filter(a -> !a.startsWith("-")).toList();
        if (!draft && !fin && files.isEmpty()) {
            System.out.println(MessageConstants.MSG_CHECKIN_USAGE);
            return 1;
        }
        if (files.isEmpty()) {
            System.out.println(MessageConstants.MSG_NO_FILES_SPECIFIED);
            return 1;
        }
        String message = "Commit from checkin";
        int mIdx = args.indexOf("-m");
        if (mIdx >= 0 && mIdx + 1 < args.size()) {
            message = args.get(mIdx + 1);
        }
        interactive = true;
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
            String warn = MessageConstants.MSG_CHECKIN_ERR_NO_REPO;
            System.err.println(warn);
            return 1;
        }
        try (Git git1 = repoRes.getGit()) {
            for (String file : files) {
                git1.add().addFilepattern(file).call();
            }
            git1.commit().setMessage(message).call();
            System.out.println(MessageConstants.MSG_COMMIT_SUCCESS);
        }
        return 0;
    }
}
