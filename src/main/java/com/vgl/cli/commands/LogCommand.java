package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class LogCommand implements Command {
    @Override
    public String name() {
        return "log";
    }

    @Override
    public int run(List<String> args) throws Exception {
        if (args.contains("-h") || args.contains("--help")) {
            System.out.println(Messages.logUsage());
            return 0;
        }

        boolean verbose = args.contains("-v") || args.contains("-vv");
        boolean veryVerbose = args.contains("-vv");

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            Iterable<RevCommit> logs = git.log().call();
            for (RevCommit c : logs) {
                String id = c.getId().abbreviate(7).name();
                String msg = oneLine(c.getFullMessage());
                if (!veryVerbose) {
                    msg = truncateEnd(msg, 80);
                }

                if (verbose) {
                    String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                    String author = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
                    if (!author.isBlank()) {
                        System.out.println(id + "  " + date + "  " + msg + "  (" + author + ")");
                    } else {
                        System.out.println(id + "  " + date + "  " + msg);
                    }
                } else {
                    System.out.println(id + " " + msg);
                }
            }
            return 0;
        }
    }

    private static String oneLine(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncateEnd(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (maxLen <= 0 || s.length() <= maxLen) {
            return s;
        }
        if (maxLen <= 3) {
            return s.substring(0, maxLen);
        }
        return s.substring(0, maxLen - 3) + "...";
    }
}
