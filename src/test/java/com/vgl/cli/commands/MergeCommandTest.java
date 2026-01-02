package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MergeCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void merge_fromBranchIntoCurrent_bringsChanges() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");

            RepoTestUtils.writeFile(repoDir, "base.txt", "base\n");
            git.add().addFilepattern("base.txt").call();
            git.commit().setMessage("base").setAuthor(ident).setCommitter(ident).call();

            git.checkout().setCreateBranch(true).setName("feature").call();
            RepoTestUtils.writeFile(repoDir, "feature.txt", "feature\n");
            git.add().addFilepattern("feature.txt").call();
            git.commit().setMessage("feature").setAuthor(ident).setCommitter(ident).call();

            git.checkout().setName("main").call();
        }

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"merge", "-lb", "feature"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("Merged feature into main.");
        }

        assertThat(Files.exists(repoDir.resolve("feature.txt"))).isTrue();
    }
}
