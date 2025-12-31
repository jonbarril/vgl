package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.commands.helpers.Usage;
import com.vgl.cli.utils.Messages;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeleteCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRepo_removesGitMetadataButKeepsWorkspaceFiles() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve("a.txt"), "hello", StandardCharsets.UTF_8);

        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(0);
        }
        assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.isRegularFile(repoDir.resolve(".vgl"))).isTrue();

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(new DeleteCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.deletedRepoMetadata(repoDir.toAbsolutePath().normalize()));
        }

        assertThat(Files.exists(repoDir.resolve("a.txt"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".git"))).isFalse();
        assertThat(Files.exists(repoDir.resolve(".vgl"))).isFalse();
        assertThat(Files.exists(repoDir.resolve(".gitignore"))).isFalse();
    }

    @Test
    void deleteRepo_withoutForce_inNoninteractiveMode_refusesSafely() throws Exception {
        String prior = System.getProperty("vgl.noninteractive");
        System.setProperty("vgl.noninteractive", "true");
        try {
            Path repoDir = tempDir.resolve("repo");
            try (StdIoCapture ignored = new StdIoCapture()) {
                assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(0);
            }

            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(new DeleteCommand().run(List.of("-lr", repoDir.toString()))).isEqualTo(1);
                assertThat(io.stdout()).isEmpty();
                assertThat(io.stderr()).isEqualTo(Messages.deleteRepoRefusing());
            }
        } finally {
            if (prior == null) {
                System.clearProperty("vgl.noninteractive");
            } else {
                System.setProperty("vgl.noninteractive", prior);
            }
        }
    }

    @Test
    void deleteBranch_deletesNonCurrentBranch() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // JGit cannot create a branch in a repo with no commits (HEAD unresolved), so seed an initial commit.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit()
                .setAllowEmpty(true)
                .setMessage("init")
                .setAuthor(ident)
                .setCommitter(ident)
                .call();
        }

        try (Git git = Git.open(repoDir.toFile())) {
            git.branchCreate().setName("feature").call();
            assertThat(git.getRepository().findRef("refs/heads/feature")).isNotNull();
        }

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(new DeleteCommand().run(List.of("-lr", repoDir.toString(), "-lb", "feature", "-f"))).isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).isEqualTo(Messages.deletedBranch("feature"));
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().findRef("refs/heads/feature")).isNull();
        }
    }

    @Test
    void deleteBranch_refusesCurrentBranch() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(new DeleteCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.refusingDeleteCurrentBranch("main"));
        }
    }

    @Test
    void deleteRepo_whenTargetIsNotRepoRoot_doesNotDeleteAncestorRepo() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(0);
        }

        Path subdir = repoDir.resolve("dir0");
        Files.createDirectories(subdir);

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"delete", "-lr", subdir.toString(), "-f"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.noRepoAtTarget(subdir.toAbsolutePath().normalize()));
        }

        // Ensure the ancestor repo still exists.
        assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.isRegularFile(repoDir.resolve(".vgl"))).isTrue();
    }

    @Test
    void delete_withUnknownFlag_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"delete", "--nope"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.delete());
        }
    }

    @Test
    void delete_withTooManyPositionals_printsUsage() throws Exception {
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"delete", a.toString(), b.toString(), "-f"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.delete());
        }
    }
}
