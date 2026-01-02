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

class RestoreCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void restore_replacesWorkingFileFromHead() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        RepoTestUtils.createVglRepo(repoDir);

        RepoTestUtils.writeFile(repoDir, "file.txt", "original\n");
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern("file.txt").call();
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        RepoTestUtils.writeFile(repoDir, "file.txt", "modified\n");

        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"restore", "-f", "file.txt"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("Files to restore:");
            assertThat(io.stdout()).contains("Restored 1 file(s).");
        }

        assertThat(RepoTestUtils.readFile(repoDir, "file.txt")).isEqualTo("original\n");
    }
}
