package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.utils.Messages;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void commit_whenNoChanges_printsNoChangesAndExits0() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed an initial commit so repo isn't unborn.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoDir.toString());
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"commit", "msg"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();
                assertThat(io.stdout()).isEqualTo("No changes to commit.");
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }
    }

    @Test
    void commit_stagesTrackedModificationsAndCommits() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        // Modify tracked file but do not stage it.
        Files.writeString(tracked, "hello2\n", StandardCharsets.UTF_8);

        // Add an undecided file so commit warns per spec.
        Files.writeString(repoDir.resolve("undecided.txt"), "u\n", StandardCharsets.UTF_8);

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoDir.toString());
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"commit", "update"})).isEqualTo(0);
                assertThat(io.stderr()).isEqualTo(Messages.commitUndecidedFilesHint());
                assertThat(io.stdout()).contains("Committed files:\n");
                assertThat(io.stdout()).contains("M tracked.txt");
                assertThat(io.stdout()).contains("Commit message:\n");
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            var status = git.status().call();
            assertThat(status.getModified()).doesNotContain("tracked.txt");
            assertThat(status.getUntracked()).doesNotContain("tracked.txt");
            assertThat(status.getAdded()).isEmpty();
            assertThat(status.getChanged()).isEmpty();
            assertThat(status.getRemoved()).isEmpty();
        }
    }

    @Test
    void commit_withLrInNonCurrentRepo_warns() throws Exception {
        Path repoDir = tempDir.resolve("repo3");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed commit + modify a file.
        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }
        Files.writeString(tracked, "hello2\n", StandardCharsets.UTF_8);

        // Add an undecided file so commit warns per spec.
        Files.writeString(repoDir.resolve("undecided.txt"), "u\n", StandardCharsets.UTF_8);

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"commit", "-lr", repoDir.toString(), "msg"})).isEqualTo(0);
                assertThat(io.stdout()).contains("Committed files:\n");
                assertThat(io.stdout()).contains("M tracked.txt");
                assertThat(io.stdout()).contains("Commit message:\n");
                assertThat(io.stderr()).contains(Messages.commitUndecidedFilesHint());
                assertThat(io.stderr()).contains(Messages.warnTargetRepoNotCurrent(repoDir.toAbsolutePath().normalize()));
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }
    }

    @Test
    void commit_new_replacesLastCommitMessage() throws Exception {
        Path repoDir = tempDir.resolve("repo4");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Make an initial commit.
        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("one").setAuthor(ident).setCommitter(ident).call();
        }

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoDir.toString());
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"commit", "-new", "two"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();
                assertThat(io.stdout()).contains("Commit message updated:");
                assertThat(io.stdout()).doesNotContain("CONTEXT:");
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertThat(head.getFullMessage()).isEqualTo("two");
        }
    }

    @Test
    void commit_add_appendsToLastCommitMessage() throws Exception {
        Path repoDir = tempDir.resolve("repo5");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Make an initial commit.
        Path tracked = repoDir.resolve("tracked.txt");
        Files.writeString(tracked, "hello\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("tracked.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("one").setAuthor(ident).setCommitter(ident).call();
        }

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoDir.toString());
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"commit", "-add", "two"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();
                assertThat(io.stdout()).contains("Commit message updated:");
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            assertThat(head.getFullMessage()).isEqualTo("one\n\ntwo");
        }
    }
}
