package com.vgl.cli.commands;

import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

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
        boolean graph = args.contains("-graph");

        String commitArg = firstPositionalOrNull(args);

        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            var logCmd = git.log();
            if (commitArg != null) {
                ObjectId start = null;
                try {
                    start = git.getRepository().resolve(commitArg);
                } catch (Exception ignored) {
                    start = null;
                }
                if (start == null) {
                    System.err.println("Error: Cannot resolve commit: " + commitArg);
                    return 1;
                }
                logCmd.add(start);
            }

            Iterable<RevCommit> logs = logCmd.call();
            for (RevCommit c : logs) {
                String id = c.getId().abbreviate(7).name();
                String date = fmt.format(Instant.ofEpochSecond(c.getCommitTime()));
                String author = (c.getAuthorIdent() != null) ? c.getAuthorIdent().getName() : "";
                String msg = oneLine(c.getFullMessage());

                // Default log format matches status -vv style (no truncation).
                String prefix = graph ? "* " : "";
                if (!author.isBlank()) {
                    System.out.println(prefix + id + "  " + date + "  " + author + "  " + msg);
                } else {
                    System.out.println(prefix + id + "  " + date + "  " + msg);
                }

                if (veryVerbose) {
                    printCommitChanges(git.getRepository(), c);
                } else if (verbose) {
                    // keep -v compatible with previous behavior (author already included)
                }
            }
            return 0;
        }
    }

    private static String firstPositionalOrNull(List<String> args) {
        if (args == null) {
            return null;
        }
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if (a.startsWith("-")) {
                continue;
            }
            return a;
        }
        return null;
    }

    private static void printCommitChanges(Repository repo, RevCommit commit) {
        if (repo == null || commit == null) {
            return;
        }

        ObjectId newTree;
        ObjectId oldTree;
        try {
            newTree = repo.resolve(commit.getName() + "^{tree}");
            oldTree = (commit.getParentCount() > 0)
                ? repo.resolve(commit.getParent(0).getName() + "^{tree}")
                : null;
        } catch (Exception e) {
            return;
        }

        if (newTree == null) {
            return;
        }

        try (ObjectReader reader = repo.newObjectReader()) {
            AbstractTreeIterator oldIter;
            if (commit.getParentCount() > 0 && oldTree != null) {
                CanonicalTreeParser oldParser = new CanonicalTreeParser();
                oldParser.reset(reader, oldTree);
                oldIter = oldParser;
            } else {
                oldIter = new EmptyTreeIterator();
            }

            CanonicalTreeParser newParser = new CanonicalTreeParser();
            newParser.reset(reader, newTree);

            try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                java.util.List<DiffEntry> diffs = df.scan(oldIter, newParser);
                if (diffs == null || diffs.isEmpty()) {
                    return;
                }

                java.util.List<String> entries = new java.util.ArrayList<>();
                for (DiffEntry d : diffs) {
                    if (d == null || d.getChangeType() == null) {
                        continue;
                    }
                    String letter = switch (d.getChangeType()) {
                        case ADD -> "A";
                        case MODIFY -> "M";
                        case DELETE -> "D";
                        case RENAME -> "R";
                        case COPY -> "C";
                    };
                    String path = switch (d.getChangeType()) {
                        case DELETE -> d.getOldPath();
                        default -> d.getNewPath();
                    };
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    entries.add("  " + letter + " " + path);
                }

                if (entries.isEmpty()) {
                    return;
                }

                // Match status compact list style: stable sorting by path.
                entries.sort((a, b) -> {
                    String ap = (a != null && a.length() > 4) ? a.substring(4) : a;
                    String bp = (b != null && b.length() > 4) ? b.substring(4) : b;
                    int c = String.valueOf(ap).compareTo(String.valueOf(bp));
                    if (c != 0) {
                        return c;
                    }
                    return String.valueOf(a).compareTo(String.valueOf(b));
                });

                for (String line : entries) {
                    System.out.println(line);
                }
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String oneLine(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    // No truncation helpers: log output is intended to be full fidelity.
}
