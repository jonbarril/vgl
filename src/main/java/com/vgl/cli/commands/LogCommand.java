package com.vgl.cli.commands;

import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogCommand implements Command {
    @Override public String name(){ return "log"; }

    @Override public int run(List<String> args) throws Exception {
        boolean v=false, vv=false, gr=false; for(String s: args){ if("-v".equals(s)) v=true; else if("-vv".equals(s)) vv=true; else if("-gr".equals(s)) gr=true; }
        try (Git git = Utils.openGit()) {
            if (git == null) return 0;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            Iterable<RevCommit> logs = git.log().call();
            for (RevCommit c : logs) {
                String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                String author = (c.getAuthorIdent()!=null? c.getAuthorIdent().getName() : "");
                System.out.printf("%s  %s  %s%s%n", c.getId().abbreviate(7).name(), date, c.getShortMessage(), (author.isEmpty()? "":"  ("+author+")"));
            }
        }
        return 0;
    }
}
