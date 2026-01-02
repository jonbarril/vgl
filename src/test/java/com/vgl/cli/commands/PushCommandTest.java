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

class PushCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void push_withNoRemoteConfigured_errors() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"push"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.pushNoRemoteConfigured());
        }
    }

    @Test
    void push_updatesRemoteHead() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        Path remoteDir = tempDir.resolve("remote.git");

        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.initBareRemote(remoteDir);
        RepoTestUtils.setVglRemote(repoDir, remoteDir, "main");

        ObjectId localHead;
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("one").setAuthor(ident).setCommitter(ident).call();
            localHead = git.getRepository().resolve("HEAD");
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"push"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.pushed());
        }

        try (Git remote = Git.open(remoteDir.toFile())) {
            ObjectId remoteHead = remote.getRepository().resolve("refs/heads/main");
            assertThat(remoteHead).isEqualTo(localHead);
        }
    }
}
