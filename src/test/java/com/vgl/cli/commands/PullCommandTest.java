package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PullCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void pull_fastForwardsFromOrigin() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        Path remoteDir = tempDir.resolve("remote.git");
        Path remoteWork = tempDir.resolve("remoteWork");

        RepoTestUtils.createVglRepo(repoDir);
        RepoTestUtils.initBareRemote(remoteDir);
        RepoTestUtils.setVglRemote(repoDir, remoteDir, "main");

        // Local: initial commit and push to remote (setup only).
        try (Git local = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            RepoTestUtils.writeFile(repoDir, "file.txt", "one\n");
            local.add().addFilepattern("file.txt").call();
            local.commit().setMessage("one").setAuthor(ident).setCommitter(ident).call();

            local.remoteAdd().setName("origin").setUri(new URIish(remoteDir.toUri().toString())).call();
            local.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }

        // Remote: add another commit.
        try (Git cloned = Git.cloneRepository().setURI(remoteDir.toUri().toString()).setDirectory(remoteWork.toFile()).call()) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            RepoTestUtils.writeFile(remoteWork, "file.txt", "two\n");
            cloned.add().addFilepattern("file.txt").call();
            cloned.commit().setMessage("two").setAuthor(ident).setCommitter(ident).call();
            cloned.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }

        // Local: pull.
        try (UserDirOverride ignored = new UserDirOverride(repoDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"pull", "-f"})).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.pullCompleted());
        }

        String content = RepoTestUtils.readFile(repoDir, "file.txt");
        content = content.replace("\r\n", "\n");
        assertThat(content).isEqualTo("two\n");
    }
}
