package com.vgl.cli.commands;

import com.vgl.cli.commands.helpers.ArgsHelper;
import com.vgl.cli.commands.helpers.StatusVerboseOutput;
import com.vgl.cli.commands.helpers.Usage;
import com.vgl.cli.commands.helpers.CommandWarnings;
import com.vgl.cli.utils.GitUtils;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.RepoResolver;
import com.vgl.cli.utils.RepoUtils;
import com.vgl.cli.utils.Utils;
import com.vgl.cli.utils.VglConfig;
import java.util.Comparator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitCommand implements Command {
    @Override
    public String name() {
        return "commit";
    }

    @Override
    public int run(List<String> args) throws Exception {
        boolean force = args != null && args.contains("-f");

        CommitMode mode = CommitMode.NORMAL;
        String message = null;

        String newMsg = ArgsHelper.valueAfterFlag(args, "-new");
        String addMsg = ArgsHelper.valueAfterFlag(args, "-add");
        if (newMsg != null && addMsg != null) {
            System.err.println(Usage.commit());
            return 1;
        }
        if (newMsg != null) {
            mode = CommitMode.AMEND_REPLACE;
            message = newMsg;
        } else if (addMsg != null) {
            mode = CommitMode.AMEND_APPEND;
            message = addMsg;
        } else {
            List<String> positionals = collectPositionals(args);
            if (positionals.size() != 1) {
                System.err.println(Usage.commit());
                return 1;
            }
            message = positionals.get(0);
        }

        if (message == null || message.isBlank()) {
            System.err.println(Usage.commit());
            return 1;
        }

        Path explicitTargetDir = ArgsHelper.pathAfterFlag(args, "-lr");
        boolean hasExplicitTarget = explicitTargetDir != null;

        Path startDir = hasExplicitTarget ? explicitTargetDir : Path.of(System.getProperty("user.dir"));
        startDir = startDir.toAbsolutePath().normalize();

        Path repoRoot = RepoResolver.resolveRepoRootForCommand(startDir);
        if (repoRoot == null) {
            return 1;
        }

        try (Git git = GitUtils.openGit(repoRoot)) {
            if (mode == CommitMode.AMEND_REPLACE || mode == CommitMode.AMEND_APPEND) {
                if (!GitUtils.hasCommits(git.getRepository())) {
                    System.err.println("No commits to amend.");
                    return 1;
                }

                String amendedMessage = message;
                if (mode == CommitMode.AMEND_APPEND) {
                    String prior = readHeadMessageOrEmpty(git);
                    if (prior.isBlank()) {
                        amendedMessage = message;
                    } else {
                        amendedMessage = prior + "\n\n" + message;
                    }
                }

                PersonIdent ident = defaultIdent();
                RevCommit rc = git.commit().setAmend(true).setMessage(amendedMessage).setAuthor(ident).setCommitter(ident).call();

                String shortId = shortIdOrEmpty(rc);
                String oneLine = oneLineMessage(amendedMessage);
                System.out.println("Commit message updated:");
                System.out.println("  " + (shortId.isBlank() ? "" : shortId + " ") + oneLine);
            } else {
                Status status;
                Map<String, String> staged;
                boolean hasStagedChanges;

                while (true) {
                    // Stage tracked modifications/deletions. This intentionally does not add new untracked files.
                    try {
                        git.add().addFilepattern(".").setUpdate(true).call();
                    } catch (Exception ignored) {
                        // best-effort
                    }

                    status = git.status().call();
                    staged = collectStaged(status);
                    hasStagedChanges = !status.getAdded().isEmpty()
                        || !status.getChanged().isEmpty()
                        || !status.getRemoved().isEmpty();

                    // Warn if any undecided files exist (per use-cases spec).
                    // Do this even when there are no commit-eligible changes, so users
                    // get a hint why their new file didn't commit (it is likely undecided).
                    boolean hasUndecided = hasUndecidedFiles(repoRoot, status);
                    if (hasUndecided) {
                        char choice = CommandWarnings.warnHintAndMaybePromptChoice(
                            Messages.commitUndecidedFilesHint(),
                            force,
                            "Action? [A]bort / [C]ontinue / [T]rack-all then continue [C]: ",
                            'c',
                            'a',
                            'c',
                            't'
                        );
                        if (choice == 'a') {
                            System.out.println("Commit cancelled.");
                            return 0;
                        }
                        if (choice == 't') {
                            // Apply the hint by tracking all undecided files, then re-check status.
                            runTrackAllAtRepoRoot(repoRoot);
                            continue;
                        }
                    }

                    break;
                }

                if (!hasStagedChanges) {
                    System.out.println("No changes to commit.");
                    return 0;
                }

                PersonIdent ident = defaultIdent();
                RevCommit rc = git.commit().setMessage(message).setAuthor(ident).setCommitter(ident).call();
                System.out.println("Committed files:");

                if (staged.isEmpty()) {
                    System.out.println("  (none)");
                } else {
                    List<String> entries = new ArrayList<>();
                    staged.forEach((path, letter) -> {
                        if (path == null || path.isBlank()) {
                            return;
                        }
                        String l = (letter == null || letter.isBlank()) ? "M" : letter;
                        entries.add(l + " " + path);
                    });
                    entries.sort(commitEntryComparator());
                    StatusVerboseOutput.printCompactEntries("", entries);
                }

                String shortId = shortIdOrEmpty(rc);
                String oneLine = oneLineMessage(message);
                System.out.println("Commit message:");
                System.out.println("  " + (shortId.isBlank() ? "" : shortId + " ") + oneLine);
            }
        }

        // Keep a warning for explicit -lr when that repo is not the current repo.
        if (hasExplicitTarget) {
            Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path cwdRepoRoot = RepoUtils.findNearestRepoRoot(cwd);
            if (cwdRepoRoot == null || !cwdRepoRoot.toAbsolutePath().normalize().equals(repoRoot)) {
                com.vgl.cli.commands.helpers.CommandWarnings.warnTargetRepoNotCurrent(repoRoot);
            }
        }

        return 0;
    }

    private static void runTrackAllAtRepoRoot(Path repoRoot) throws Exception {
        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoRoot.toString());
            new TrackCommand().run(List.of("-all"));
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }
    }

    private static boolean hasUndecidedFiles(Path repoRoot, Status status) {
        if (repoRoot == null) {
            return false;
        }
        try {
            if (status == null) {
                return false;
            }

            Set<String> gitUntracked = status.getUntracked();
            if (gitUntracked == null || gitUntracked.isEmpty()) {
                return false;
            }

            // Remove VGL metadata.
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
            for (String p : gitUntracked) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                String norm = p.replace('\\', '/');
                if (".vgl".equals(norm) || ".git".equals(norm)) {
                    continue;
                }
                // Avoid noisy warnings in brand new repos where only .gitignore is undecided.
                if (".gitignore".equals(norm)) {
                    continue;
                }
                candidates.add(norm);
            }
            if (candidates.isEmpty()) {
                return false;
            }

            // Remove nested-repo content.
            java.util.Set<String> nested = GitUtils.listNestedRepos(repoRoot);
            candidates.removeIf(p -> isInsideAnyNestedRepo(p, nested));
            if (candidates.isEmpty()) {
                return false;
            }

            Properties props = VglConfig.readProps(repoRoot);
            Set<String> decidedTracked = VglConfig.getPathSet(props, VglConfig.KEY_TRACKED_FILES);
            Set<String> decidedUntracked = VglConfig.getPathSet(props, VglConfig.KEY_UNTRACKED_FILES);

            for (String p : candidates) {
                if (decidedTracked.contains(p) || decidedUntracked.contains(p)) {
                    continue;
                }
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isInsideAnyNestedRepo(String relPath, java.util.Set<String> nestedRepos) {
        if (relPath == null || nestedRepos == null || nestedRepos.isEmpty()) {
            return false;
        }
        for (String n : nestedRepos) {
            if (n == null || n.isBlank()) {
                continue;
            }
            if (relPath.equals(n) || relPath.startsWith(n + "/")) {
                return true;
            }
        }
        return false;
    }

    private enum CommitMode {
        NORMAL,
        AMEND_REPLACE,
        AMEND_APPEND
    }

    private static String readHeadMessageOrEmpty(Git git) {
        if (git == null) {
            return "";
        }
        try {
            Iterable<RevCommit> logs = git.log().setMaxCount(1).call();
            for (RevCommit c : logs) {
                String m = c.getFullMessage();
                return m != null ? m : "";
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return "";
    }

    private static Map<String, String> collectStaged(Status status) {
        if (status == null) {
            return Map.of();
        }
        // Keep stable ordering by sorting by path.
        Map<String, String> out = new TreeMap<>();
        for (String p : status.getAdded()) {
            if (p != null && !p.isBlank()) {
                out.put(p.replace('\\', '/'), "A");
            }
        }
        for (String p : status.getChanged()) {
            if (p != null && !p.isBlank()) {
                out.put(p.replace('\\', '/'), "M");
            }
        }
        for (String p : status.getRemoved()) {
            if (p != null && !p.isBlank()) {
                out.put(p.replace('\\', '/'), "D");
            }
        }
        return out;
    }

    private static Comparator<String> commitEntryComparator() {
        return (a, b) -> {
            String ap = (a != null && a.length() > 2) ? a.substring(2) : a;
            String bp = (b != null && b.length() > 2) ? b.substring(2) : b;
            int c = String.valueOf(ap).compareTo(String.valueOf(bp));
            if (c != 0) {
                return c;
            }
            return String.valueOf(a).compareTo(String.valueOf(b));
        };
    }

    private static String shortIdOrEmpty(RevCommit rc) {
        if (rc == null) {
            return "";
        }
        String n = rc.getName();
        if (n == null || n.isBlank()) {
            return "";
        }
        return (n.length() >= 7) ? n.substring(0, 7) : n;
    }

    private static String oneLineMessage(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static PersonIdent defaultIdent() {
        String name = System.getProperty("user.name");
        if (name == null || name.isBlank()) {
            name = "vgl";
        }
        String email = System.getProperty("user.email");
        if (email == null || email.isBlank()) {
            email = name + "@localhost";
        }
        return new PersonIdent(name, email);
    }

    private static List<String> collectPositionals(List<String> args) {
        List<String> out = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return out;
        }

        // Mirror ArgsHelper.firstPositionalOrNull behavior, but collect all.
        List<String> flagsWithValue = List.of("-lr", "-lb", "-bb", "-rr", "-rb");
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token == null) {
                continue;
            }
            if (token.startsWith("-")) {
                if (flagsWithValue.contains(token)) {
                    i++; // skip the value
                }
                continue;
            }
            out.add(token);
        }
        return out;
    }
}
