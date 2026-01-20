package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void log_printsCommitMessages() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");

            RepoTestUtils.writeFile(repoDir, "a.txt", "a\n");
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();

            RepoTestUtils.writeFile(repoDir, "a.txt", "b\n");
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("second").setAuthor(ident).setCommitter(ident).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"log"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("init");
            assertThat(io.stdout()).contains("second");
        }
    }

    @Test
    void log_verbose_includesAuthorName() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        RepoTestUtils.createVglRepo(repoDir);

        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            RepoTestUtils.writeFile(repoDir, "a.txt", "a\n");
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"log", "-v"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("test");
        }
    }

    @Test
    void log_verbose_withCommitArg_printsOnlyThatCommit() throws Exception {
        Path repoDir = tempDir.resolve("repo4");
        RepoTestUtils.createVglRepo(repoDir);

        String firstId;
        String secondId;
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");

            RepoTestUtils.writeFile(repoDir, "a.txt", "a\n");
            git.add().addFilepattern("a.txt").call();
            firstId = git.commit().setMessage("first").setAuthor(ident).setCommitter(ident).call().getId().name();

            RepoTestUtils.writeFile(repoDir, "a.txt", "b\n");
            git.add().addFilepattern("a.txt").call();
            secondId = git.commit().setMessage("second").setAuthor(ident).setCommitter(ident).call().getId().name();
        }

        // Filter by the newest commit; output should not include the parent commit.
        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"log", "-v", secondId.substring(0, 7)})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("second");
            assertThat(io.stdout()).doesNotContain("first");
            assertThat(io.stdout()).doesNotContain(firstId.substring(0, 7));
        }
    }

    @Test
    void log_veryVerbose_includesFileChanges() throws Exception {
        Path repoDir = tempDir.resolve("repo3");
        RepoTestUtils.createVglRepo(repoDir);

        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            RepoTestUtils.writeFile(repoDir, "a.txt", "a\n");
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"log", "-vv"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/a.txt b/a.txt");
            assertThat(io.stdout()).contains("+++ b/a.txt");
        }
    }
}
