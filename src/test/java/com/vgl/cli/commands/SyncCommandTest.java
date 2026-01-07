package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void sync_noop_printsDryRun() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        Path remoteDir = tempDir.resolve("remote.git");
        
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");
        RepoTestUtils.initBareRemote(remoteDir);
        RepoTestUtils.setVglRemote(repoDir, remoteDir, "main");

        // Push initial commit to remote so pull can fetch from it
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir.toFile())) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(remoteDir.toUri().toString())).call();
            git.push().setRemote("origin").setRefSpecs(new org.eclipse.jgit.transport.RefSpec("refs/heads/main:refs/heads/main")).call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"sync", "-noop"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            // Sync -noop now delegates to pull -noop which reports merge analysis
            assertThat(io.stdout()).contains("Dry run:");
        }
    }

    @Test
    void sync_withoutRemote_failsLikePull() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"sync", "-f"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.pushNoRemoteConfigured());
        }
    }
}
