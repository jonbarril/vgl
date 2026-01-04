package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SplitCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void split_into_createsBranchAndSwitches() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"split", "-into", "-lb", "feature"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).startsWith(Messages.createdAndSwitchedBranch("feature") + "\n");
            assertThat(io.stdout()).contains("LOCAL:");
            assertThat(io.stdout()).contains("REMOTE:");
            assertThat(io.stdout()).contains(":: feature");
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("feature");
        }
    }

    @Test
    void split_into_unbornRepo_switchesWithoutHeadError() throws Exception {
        Path repoDir = tempDir.resolve("repo-unborn");
        RepoTestUtils.createVglRepo(repoDir);

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"split", "-into", "-lb", "feature"})).isEqualTo(0);
            assertThat(io.stderr()).doesNotContain("Ref HEAD cannot be resolved");
            assertThat(io.stdout()).startsWith(Messages.createdAndSwitchedBranch("feature") + "\n");
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("feature");
        }
    }
}
