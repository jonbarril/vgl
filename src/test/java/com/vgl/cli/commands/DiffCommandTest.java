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
}
