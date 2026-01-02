package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbortCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void abort_whenMergeInProgress_clearsMergeState() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        // Base commit with file.
        RepoTestUtils.writeFile(repoDir, "file.txt", "base\n");
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("file.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("base").setAuthor(ident).setCommitter(ident).call();

            // feature: change file
            git.checkout().setCreateBranch(true).setName("feature").call();
            RepoTestUtils.writeFile(repoDir, "file.txt", "feature\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("feature").setAuthor(ident).setCommitter(ident).call();

            // main: conflicting change
            git.checkout().setName("main").call();
            RepoTestUtils.writeFile(repoDir, "file.txt", "main\n");
            git.add().addFilepattern("file.txt").call();
            git.commit().setMessage("main").setAuthor(ident).setCommitter(ident).call();

            Ref featureRef = git.getRepository().findRef("feature");
            MergeResult r = git.merge().include(featureRef).call();
            assertThat(r.getMergeStatus().isSuccessful()).isFalse();
        }

        Path mergeHead = repoDir.resolve(".git").resolve("MERGE_HEAD");
        assertThat(Files.exists(mergeHead)).isTrue();

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"abort"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.abortCompleted());
        }

        assertThat(Files.exists(mergeHead)).isFalse();
    }

    @Test
    void abort_whenNothingToAbort_printsMessage() throws Exception {
        Path repoDir = tempDir.resolve("repo2");
        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.seedEmptyCommit(repoDir, "init");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"abort"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.abortNothingToAbort());
        }
    }
}
