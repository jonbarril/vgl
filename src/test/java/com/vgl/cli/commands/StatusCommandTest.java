package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.utils.Messages;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
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

            // List items are indented. Output may be compact/wrapped where multiple items
            // appear on a single indented line separated by two spaces.
            if (line.startsWith("  ")) {
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
