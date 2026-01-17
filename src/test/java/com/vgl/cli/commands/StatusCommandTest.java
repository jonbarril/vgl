package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.VglConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatusCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void status_whenNoRepoFound_printsHintAndExits1() {
        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        try {
            System.setProperty("user.dir", tempDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                int exit = VglMain.run(new String[] {"status"});
                assertThat(exit).isEqualTo(1);
                assertThat(io.stdout()).isEmpty();
                assertThat(io.stderr()).isEqualTo(Messages.statusNoRepoFoundHint());
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
            if (priorBase == null) {
                System.clearProperty("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", priorBase);
            }
        }
    }

    @Test
    void status_verbose_undecidedCountMatchesListSize() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Commit a tracked file so Tracked isn't empty.
        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("add tracked").setAuthor(ident).setCommitter(ident).call();
        }

        // Create one undecided (git-untracked) file.
        Files.writeString(repoDir.resolve("undecided.txt"), "u\n", StandardCharsets.UTF_8);

        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        try {
            System.setProperty("user.dir", repoDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                int exit = VglMain.run(new String[] {"status", "-v", "-files"});
                assertThat(exit).isEqualTo(0);

                String out = io.stdout();
                assertThat(io.stderr()).isEmpty();

                int undecidedSummary = parseCount(out, Pattern.compile("(\\d+) Undecided"));
                int undecidedListed = countListItems(out, "-- Undecided Files:");
                assertThat(undecidedListed).isEqualTo(undecidedSummary);
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
            if (priorBase == null) {
                System.clearProperty("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", priorBase);
            }
        }
    }

    @Test
    void status_veryVerbose_trackedCountMatchesListSize() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("add tracked").setAuthor(ident).setCommitter(ident).call();
        }

        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        try {
            System.setProperty("user.dir", repoDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                int exit = VglMain.run(new String[] {"status", "-vv", "-files"});
                assertThat(exit).isEqualTo(0);

                String out = io.stdout();
                assertThat(io.stderr()).isEmpty();

                int trackedSummary = parseCount(out, Pattern.compile("(\\d+) Tracked"));
                int trackedListed = countListItems(out, "-- Tracked Files:");
                assertThat(trackedListed).isEqualTo(trackedSummary);
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
            if (priorBase == null) {
                System.clearProperty("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", priorBase);
            }
        }
    }

    @Test
    void status_veryVerbose_showsVglUntrackedFiles_notUndecided() throws Exception {
        Path repoDir = tempDir.resolve("repo3");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        try {
            System.setProperty("user.dir", repoDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());

            // Make a file that is first tracked, then explicitly untracked via VGL.
            Files.writeString(repoDir.resolve("a.txt"), "hello\n", StandardCharsets.UTF_8);
            try (StdIoCapture ignored = new StdIoCapture()) {
                assertThat(new TrackCommand().run(List.of("a.txt"))).isEqualTo(0);
            }
            try (StdIoCapture ignored = new StdIoCapture()) {
                assertThat(new UntrackCommand().run(List.of("a.txt"))).isEqualTo(0);
            }

            // Sanity: Git now considers it untracked in the working tree.
            try (Git git = Git.open(repoDir.toFile())) {
                assertThat(git.status().call().getUntracked()).contains("a.txt");
            }

            try (StdIoCapture io = new StdIoCapture()) {
                int exit = VglMain.run(new String[] {"status", "-vv", "-files"});
                assertThat(exit).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();

                String out = io.stdout();
                assertThat(out).contains("-- Untracked Files:");
                assertThat(out).contains("a.txt");

                // It should not be classified as Undecided (other undecided files may exist, e.g. .gitignore).
                String undecidedBlock = sectionBody(out, "-- Undecided Files:");
                assertThat(undecidedBlock).doesNotContain("a.txt");

                int undecidedSummary = parseCount(out, Pattern.compile("(\\d+) Undecided"));
                int undecidedListed = countListItems(out, "-- Undecided Files:");
                assertThat(undecidedListed).isEqualTo(undecidedSummary);

                int untrackedSummary = parseCount(out, Pattern.compile("(\\d+) Untracked"));
                int untrackedListed = countListItems(out, "-- Untracked Files:");
                assertThat(untrackedListed).isEqualTo(untrackedSummary);
                assertThat(untrackedListed).isEqualTo(1);
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
            if (priorBase == null) {
                System.clearProperty("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", priorBase);
            }
        }
    }

    @Test
    void status_whenGitOnlyAndUserConverts_preservesRemoteInVgl() throws Exception {
        Path repoDir = tempDir.resolve("repo_git_only");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            StoredConfig cfg = git.getRepository().getConfig();
            cfg.setString("remote", "origin", "url", "https://example.com/repo.git");
            cfg.setString("branch", "main", "remote", "origin");
            cfg.setString("branch", "main", "merge", "refs/heads/main");
            cfg.save();
        }

        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        String priorForceInteractive = System.getProperty("vgl.force.interactive");
        String priorNonInteractive = System.getProperty("vgl.noninteractive");

        var priorIn = System.in;
        var priorOut = System.out;
        var priorErr = System.err;

        try {
            System.setProperty("user.dir", repoDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());
            System.setProperty("vgl.force.interactive", "true");
            System.clearProperty("vgl.noninteractive");

            System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));
            ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
            ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));

            int exit = VglMain.run(new String[] {"status"});
            assertThat(exit).isEqualTo(0);

            // Remote context is persisted into .vgl during conversion.
            var props = VglConfig.readProps(repoDir);
            assertThat(props.getProperty(VglConfig.KEY_REMOTE_URL, "")).isEqualTo("https://example.com/repo.git");
            assertThat(props.getProperty(VglConfig.KEY_REMOTE_BRANCH, "")).isEqualTo("main");
        } finally {
            System.setIn(priorIn);
            System.setOut(priorOut);
            System.setErr(priorErr);

            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
            if (priorBase == null) {
                System.clearProperty("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", priorBase);
            }
            if (priorForceInteractive == null) {
                System.clearProperty("vgl.force.interactive");
            } else {
                System.setProperty("vgl.force.interactive", priorForceInteractive);
            }
            if (priorNonInteractive == null) {
                System.clearProperty("vgl.noninteractive");
            } else {
                System.setProperty("vgl.noninteractive", priorNonInteractive);
            }
        }
    }

    private static int parseCount(String out, Pattern pattern) {
        Matcher m = pattern.matcher(out);
        if (!m.find()) {
            return 0;
        }
        return Integer.parseInt(m.group(1));
    }

    private static int countListItems(String out, String header) {
        String[] lines = out.split("\\n");
        boolean in = false;
        int count = 0;
        for (String line : lines) {
            if (line.equals(header)) {
                in = true;
                continue;
            }
            if (!in) {
                continue;
            }
            if (line.startsWith("-- ")) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equals("(none)")) {
                continue;
            }
            if (trimmed.startsWith("(none)")) {
                continue;
            }

            // Compact ls-style output:
            // - Root items can be on 2-space indented lines in horizontal columns.
            // - Expanded directory blocks have a 2-space indented dir header like 'dir0/'
            //   followed by 4-space indented lines with horizontal columns of leaf names.
            // - Ignore dir header lines (they are not items).
            if (line.startsWith("  ")) {
                // Directory header line: two-space indent + single token ending in '/'
                // (avoid misclassifying ignored entries like '.git/' and '@repo/' which start with '.'/'@').
                if (line.startsWith("  ") && !line.startsWith("    ")) {
                    if (trimmed.endsWith("/") && !trimmed.startsWith(".") && !trimmed.startsWith("@") && !trimmed.contains(" ")) {
                        continue;
                    }
                }

                String[] items = trimmed.split("\\s{2,}");
                for (String item : items) {
                    if (item != null && !item.isBlank()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String sectionBody(String out, String header) {
        if (out == null || header == null) {
            return "";
        }
        String normalized = out.replace("\r\n", "\n");
        String needle = header + "\n";
        int start = normalized.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int from = start + needle.length();
        int next = normalized.indexOf("\n-- ", from);
        if (next < 0) {
            next = normalized.length();
        }
        return normalized.substring(from, next);
    }
}
