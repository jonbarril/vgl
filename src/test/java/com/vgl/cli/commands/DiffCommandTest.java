package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.VglConfig;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import java.util.Properties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
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
    void diff_bareFilenameMatchesNestedPath() throws Exception {
        Path repoDir = tempDir.resolve("repo_nested");
        RepoTestUtils.createVglRepo(repoDir);

        String nested = "src/main/java/vv/subsystems/drivetrain/DrivetrainSubsystem.java";
        RepoTestUtils.writeFile(repoDir, nested, "one\n");
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern(nested).call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        RepoTestUtils.writeFile(repoDir, nested, "two\n");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"diff", "DrivetrainSubsystem.java"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/" + nested + " b/" + nested);
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }

    @Test
    void diff_defaultDoesNotUseRemoteJustBecauseConfigured() throws Exception {
        Path repoDir = tempDir.resolve("repo_remote_configured");
        RepoTestUtils.createVglRepo(repoDir);

        RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("file.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        // Configure a remote in .vgl, but make it invalid; default diff must NOT try to use it.
        Properties props = VglConfig.readProps(repoDir);
        props.setProperty(VglConfig.KEY_REMOTE_URL, "https://example.invalid/repo.git");
        props.setProperty(VglConfig.KEY_REMOTE_BRANCH, "main");
        VglConfig.writeProps(repoDir, p -> {
            p.setProperty(VglConfig.KEY_REMOTE_URL, props.getProperty(VglConfig.KEY_REMOTE_URL));
            p.setProperty(VglConfig.KEY_REMOTE_BRANCH, props.getProperty(VglConfig.KEY_REMOTE_BRANCH));
        });

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

    @Test
    void diff_showsDiffBetweenTwoRemoteRepos() throws Exception {
        Path remote1 = tempDir.resolve("remote1.git");
        Path remote2 = tempDir.resolve("remote2.git");
        RepoTestUtils.initBareRemote(remote1);
        RepoTestUtils.initBareRemote(remote2);

        // Seed remote1 with file.txt=one
        Path seed1 = tempDir.resolve("seed1");
        PersonIdent ident = new PersonIdent("test", "test@example.com");
        try (Git git = Git.init().setInitialBranch("main").setDirectory(seed1.toFile()).call()) {
            RepoTestUtils.writeFile(seed1, "file.txt", "one\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("seed1").setAuthor(ident).setCommitter(ident).call();
            git.remoteAdd().setName("origin").setUri(new URIish(remote1.toUri().toString())).call();
            git.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }

        // Seed remote2 with file.txt=two
        Path seed2 = tempDir.resolve("seed2");
        try (Git git = Git.init().setInitialBranch("main").setDirectory(seed2.toFile()).call()) {
            RepoTestUtils.writeFile(seed2, "file.txt", "two\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("seed2").setAuthor(ident).setCommitter(ident).call();
            git.remoteAdd().setName("origin").setUri(new URIish(remote2.toUri().toString())).call();
            git.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(
                VglMain.run(
                    new String[] {
                        "diff",
                        "-rr",
                        remote1.toUri().toString(),
                        "-rb",
                        "main",
                        "-rr",
                        remote2.toUri().toString(),
                        "-rb",
                        "main",
                        "file.txt"
                    }
                )
            ).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("diff --git a/file.txt b/file.txt");
            assertThat(io.stdout()).contains("-one");
            assertThat(io.stdout()).contains("+two");
        }
    }
}
