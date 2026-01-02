package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckinCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void checkin_requiresExactlyOneOfDraftOrFinal() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"checkin"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.checkinUsage());
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"checkin", "-draft", "-final"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.checkinUsage());
        }
    }

    @Test
    void checkin_commitsAndPushes() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        Path remoteDir = tempDir.resolve("remote.git");

        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.initBareRemote(remoteDir);
        RepoTestUtils.setVglRemote(repoDir, remoteDir, "main");

        // Seed HEAD so later commit is not unborn.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        RepoTestUtils.writeFile(repoDir, "file.txt", "hello\n");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"checkin", "-draft", "-m", "msg"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains(Messages.pushed());
            assertThat(io.stdout()).contains(Messages.checkinCompleted(true));
        }

        try (Git remote = Git.open(remoteDir.toFile())) {
            ObjectId remoteHead = remote.getRepository().resolve("refs/heads/main");
            assertThat(remoteHead).isNotNull();
        }
    }
}
