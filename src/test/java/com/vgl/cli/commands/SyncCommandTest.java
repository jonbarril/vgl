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
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"sync", "-noop"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.syncDryRun());
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
