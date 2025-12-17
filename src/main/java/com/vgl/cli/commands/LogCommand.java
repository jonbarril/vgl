package com.vgl.cli.commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class LogCommand implements Command {
    @Override public String name(){ return "log"; }

    @Override public int run(List<String> args) throws Exception {
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        boolean interactive = true;
        com.vgl.cli.services.RepoResolution repoRes = com.vgl.cli.commands.helpers.VglRepoInitHelper.ensureVglConfig(cwd, interactive);
        if (repoRes.getGit() == null) {
            String warn = "WARNING: No VGL repository found in this directory or any parent.\n" +
                          "Hint: Run 'vgl create' to initialize a new repo here.";
            System.err.println(warn);
            System.out.println(warn);
            return 1;
        }
        try (Git git = repoRes.getGit()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            Iterable<RevCommit> logs = git.log().call();
            for (RevCommit c : logs) {
                String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                String author = (c.getAuthorIdent() != null ? c.getAuthorIdent().getName() : "");
                System.out.printf("%s  %s  %s%s%n",
                    c.getId().abbreviate(7).name(),
                    date,
                    c.getShortMessage(),
                    (author.isEmpty() ? "" : "  (" + author + ")"));
            }
        }
        return 0;
    }
}
