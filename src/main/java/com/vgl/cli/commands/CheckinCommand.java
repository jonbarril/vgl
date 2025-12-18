package com.vgl.cli.commands;

import org.eclipse.jgit.api.Git;

import java.util.List;

public class CheckinCommand implements Command {
    @Override public String name(){ return "checkin"; }

    @Override public int run(List<String> args) throws Exception {
        boolean draft = args.contains("-draft");
        boolean fin = args.contains("-final");
        if (!draft && !fin) {
            System.out.println("Usage: vgl checkin -draft|-final");
            return 1;
        }
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = true; // Could be set from args if needed
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            String branch = git.getRepository().getBranch();
            String url = git.getRepository().getConfig().getString("remote","origin","url");
            if (url != null && url.contains("github.com")) {
                String name = url.replaceFirst(".*github.com[:/]", "").replaceAll("\\.git$", "");
                System.out.println("Open your PR: https://github.com/" + name + "/compare/main..." + branch + "?expand=1" + (draft ? " (draft)" : ""));
            } else {
                System.out.println("Remote is not GitHub; open a PR in your provider.");
            }
        }
        return 0;
    }
}
