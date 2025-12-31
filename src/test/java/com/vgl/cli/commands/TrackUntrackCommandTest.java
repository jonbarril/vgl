package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.commands.helpers.Usage;
import com.vgl.cli.test.utils.StdIoCapture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackUntrackCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void track_withNoArgs_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"track"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.track());
        }
    }

    @Test
    void untrack_withNoArgs_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"untrack"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.untrack());
        }
    }

    @Test
    void track_then_untrack_updatesGitIndex() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(java.util.List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed a commit so index operations are stable.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        Files.writeString(repoDir.resolve("a.txt"), "hello\n", StandardCharsets.UTF_8);

        String priorUserDir = System.getProperty("user.dir");
        String priorBase = System.getProperty("vgl.test.base");
        try {
            System.setProperty("user.dir", repoDir.toString());
            System.setProperty("vgl.test.base", tempDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"track", "a.txt"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();
                assertThat(io.stdout()).contains("Tracking:");
            }

            try (Git git = Git.open(repoDir.toFile())) {
                assertThat(git.status().call().getUntracked()).doesNotContain("a.txt");
            }

            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"untrack", "a.txt"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();
                assertThat(io.stdout()).contains("Untracked:");
            }

            try (Git git = Git.open(repoDir.toFile())) {
                // Now it should be untracked from Git again.
                assertThat(git.status().call().getUntracked()).contains("a.txt");
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
}
