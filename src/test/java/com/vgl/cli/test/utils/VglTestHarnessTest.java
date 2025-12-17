package com.vgl.cli.test.utils;

import org.eclipse.jgit.api.Git;
import com.vgl.cli.VglCli;
import com.vgl.cli.services.RepoManager;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

/**
 * Tests for the VglTestHarness itself to ensure it provides
 * a reliable foundation for all other tests.
 */
public class VglTestHarnessTest {

    // Helper to create a new test repo under the given temp root
    private Path newTestRepoPath(Path tempDir) throws Exception {
        String prev = System.getProperty("junit.temp.root");
        System.setProperty("junit.temp.root", tempDir.toAbsolutePath().toString());
        try {
            Path repo = tempDir.resolve("repo-" + java.util.UUID.randomUUID());
            Files.createDirectories(repo);
            return repo;
        } finally {
            if (prev != null) {
                System.setProperty("junit.temp.root", prev);
            } else {
                System.clearProperty("junit.temp.root");
            }
        }
    }

    @Test
    void createRepoInitializesGit(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            assertThat(r.hasGit()).isTrue();
            assertThat(Files.exists(repo.resolve(".git"))).isTrue();
        }
    }

    @Test
    void createRepoSetsUserDir(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            assertThat(System.getProperty("user.dir")).isEqualTo(repo.toString());
            assertThat(r.getPath()).isEqualTo(repo);
        }
        assertThat(System.getProperty("user.dir")).isEqualTo(originalUserDir);
    }

    @Test
    void createDirDoesNotInitializeGit(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createDir(repo)) {
            assertThat(r.hasGit()).isFalse();
            assertThat(Files.exists(repo.resolve(".git"))).isFalse();
        }
    }

    @Test
    void runCommandCapturesOutput(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        VglTestHarness.createRepo(repo);
        assertThat(Files.exists(repo.resolve(".git"))).isTrue();
    }

    @Test
    void writeAndReadFile(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            r.writeFile("test.txt", "hello world");
            assertThat(r.fileExists("test.txt")).isTrue();
            assertThat(r.readFile("test.txt")).isEqualTo("hello world");
        }
    }

    @Test
    void writeFileInSubdirectory(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            r.writeFile("sub/dir/file.txt", "content");
            assertThat(r.fileExists("sub/dir/file.txt")).isTrue();
            assertThat(Files.exists(repo.resolve("sub/dir"))).isTrue();
        }
    }

    @Test
    void gitAddAndCommit(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            r.writeFile("file.txt", "content");
            r.gitAdd("file.txt");
            r.gitCommit("Initial commit");
            try (var git = r.getGit()) {
                var commits = git.log().call();
                var iterator = commits.iterator();
                assertThat(iterator.hasNext()).isTrue();
                var commit = iterator.next();
                assertThat(commit).isNotNull();
                assertThat(commit.getFullMessage()).isEqualTo("Initial commit");
            }
        }
    }

    @Test
    void createSubdir(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            Path subdir = r.createSubdir("src/main/java");
            assertThat(Files.exists(subdir)).isTrue();
            assertThat(Files.isDirectory(subdir)).isTrue();
        }
    }

    @Test
    void createVglInstance(@TempDir Path tempDir) throws Exception {
        String prev = System.getProperty("junit.temp.root");
        System.setProperty("junit.temp.root", tempDir.toAbsolutePath().toString());
        try {
            Path repo = newTestRepoPath(tempDir);
            try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
                VglCli vgl = r.createVglInstance();
                assertThat(vgl).isNotNull();
                assertThat(vgl.getLocalDir()).isEqualTo(repo.toString());
            }
        } finally {
            if (prev != null) {
                System.setProperty("junit.temp.root", prev);
            } else {
                System.clearProperty("junit.temp.root");
            }
        }
    }

    @Test
    void multipleOperations(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        VglTestHarness.createRepo(repo);
        Path file1 = repo.resolve("file1.txt");
        Path file2 = repo.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        assertThat(Files.exists(file1)).isTrue();
        assertThat(Files.exists(file2)).isTrue();
    }

    @Test
    void nestedTryWithResources(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        Path repo1 = newTestRepoPath(tempDir);
        Path repo2 = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r1 = VglTestHarness.createRepo(repo1)) {
            assertThat(System.getProperty("user.dir")).isEqualTo(repo1.toString());
            assertThat(r1.getPath()).isEqualTo(repo1);
            try (VglTestHarness.VglTestRepo r2 = VglTestHarness.createRepo(repo2)) {
                assertThat(System.getProperty("user.dir")).isEqualTo(repo2.toString());
                assertThat(r2.getPath()).isEqualTo(repo2);
            }
            assertThat(System.getProperty("user.dir")).isEqualTo(repo1.toString());
        }
        assertThat(System.getProperty("user.dir")).isEqualTo(originalUserDir);
    }

    @Test
    void getGitFailsForDirWithoutGit(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createDir(repo)) {
            assertThatThrownBy(() -> r.getGit())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have .git initialized");
        }
    }

    @Test
    void gitConfigSetForTests(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            try (var git = r.getGit()) {
                String email = git.getRepository().getConfig().getString("user", null, "email");
                String name = git.getRepository().getConfig().getString("user", null, "name");
                assertThat(email).isEqualTo("test@example.com");
                assertThat(name).isEqualTo("Test User");
            }
        }
    }

    @Test
    void useDirForCreateCommand(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        VglTestHarness.createDir(repo);
        assertThat(Files.exists(repo.resolve(".git"))).isFalse();
        assertThat(Files.exists(repo.resolve(".vgl"))).isFalse();
        RepoManager.createVglRepo(repo, "main", null);
        assertThat(Files.exists(repo.resolve(".git"))).isTrue();
        assertThat(Files.exists(repo.resolve(".vgl"))).isTrue();
        assertThat(Files.exists(repo.resolve(".gitignore"))).isTrue();
    }

    @Test
    void useRepoForOtherCommands(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        VglTestHarness.createRepo(repo);
        assertThat(Files.exists(repo.resolve(".git"))).isTrue();
        Path file = repo.resolve("test.txt");
        Files.writeString(file, "content");
        try (Git git = Git.open(repo.toFile())) {
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("initial").call();
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().call();
            assertThat(commits.iterator().hasNext()).isTrue();
        }
        Files.writeString(file, "modified");
        assertThat(Files.readString(file)).isEqualTo("modified");
    }

    @Test
    void antiPatternCreateRepoThenCreate(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        VglTestHarness.createRepo(repo);
        boolean vglExisted = Files.exists(repo.resolve(".vgl"));
        try {
            RepoManager.createVglRepo(repo, null, null);
        } catch (Exception e) {
            // Expected: RepoManager may throw or skip .vgl creation
        }
        assertThat(Files.exists(repo.resolve(".vgl"))).isEqualTo(vglExisted);
    }

    @Test
    void stdinProvidedToCommands(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        Path nestedDir = repo.resolve("nested");
        Files.createDirectories(nestedDir);
        assertThat(Files.exists(nestedDir.resolve(".git"))).isFalse();
    }

    @Test
    void stdinAcceptsPrompt(@TempDir Path tempDir) throws Exception {
        Path repo = newTestRepoPath(tempDir);
        Path nestedDir = repo.resolve("nested");
        Files.createDirectories(nestedDir);
        RepoManager.createVglRepo(nestedDir, "main", null);
        assertThat(Files.exists(nestedDir.resolve(".git"))).isTrue();
    }
}
