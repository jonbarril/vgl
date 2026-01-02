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
            assertThat(io.stdout()).contains("(test)");
        }
    }
}
