package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_showsWorkingTreeDiffAgainstHead() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("file.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        RepoTestUtils.writeFile(repoDir, "file.txt", "two\n");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/file.txt b/file.txt");
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }

    @Test
    void diff_showsDiffBetweenTwoCommits() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        PersonIdent ident = new PersonIdent("test", "test@example.com");
        RevCommit c1;
        RevCommit c2;
        try (Git git = Git.open(repoDir.toFile())) {
            RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
            git.add().addFilepattern("file.txt").call();
            c1 = git.commit().setMessage("c1").setAuthor(ident).setCommitter(ident).call();

            RepoTestUtils.writeFile(repoDir, "file.txt", "two\n");
            git.add().addFilepattern("file.txt").call();
            c2 = git.commit().setMessage("c2").setAuthor(ident).setCommitter(ident).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", c1.getName(), c2.getName(), "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/file.txt b/file.txt");
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }

    @Test
    void diff_showsDiffBetweenTwoLocalBranches() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        PersonIdent ident = new PersonIdent("test", "test@example.com");
        try (Git git = Git.open(repoDir.toFile())) {
            RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();

            git.branchCreate().setName("split").call();
            git.checkout().setName("split").call();
            RepoTestUtils.writeFile(repoDir, "file.txt", "two\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("change").setAuthor(ident).setCommitter(ident).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", "-lb", "main", "-lb", "split", "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/file.txt b/file.txt");
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }

    @Test
    void diff_showsDiffBetweenTwoLocalReposWorkingTrees() throws Exception {
        Path repo1 = tempDir.resolve("repo1");
        Path repo2 = tempDir.resolve("repo2");
        RepoTestUtils.createVglRepo(repo1);
        RepoTestUtils.createVglRepo(repo2);

        RepoTestUtils.writeFile(repo1, "file.txt", "one\n");
        RepoTestUtils.writeFile(repo2, "file.txt", "two\n");

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", "-lr", repo1.toString(), "-lr", repo2.toString(), "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/file.txt b/file.txt");
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }

    @Test
    void diff_noop_summarizesWorkingTreeChanges() throws Exception {
        Path repoDir = tempDir.resolve("repo3");
        RepoTestUtils.createVglRepo(repoDir);

        PersonIdent ident = new PersonIdent("test", "test@example.com");
        try (Git git = Git.open(repoDir.toFile())) {
            RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        RepoTestUtils.writeFile(repoDir, "file.txt", "two\n");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", "-noop", "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.diffDryRunSummary(1));
        }
    }
}
