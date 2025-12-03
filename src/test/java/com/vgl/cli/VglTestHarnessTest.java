package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

/**
 * Tests for the VglTestHarness itself to ensure it provides
 * a reliable foundation for all other tests.
 */
public class VglTestHarnessTest {

    @Test
    void createRepoInitializesGit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            assertThat(repo.hasGit()).isTrue();
            assertThat(Files.exists(tmp.resolve(".git"))).isTrue();
        }
    }

    @Test
    void createRepoSetsUserDir(@TempDir Path tmp) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // user.dir should be set to repo path
            assertThat(System.getProperty("user.dir")).isEqualTo(tmp.toString());
            assertThat(repo.getPath()).isEqualTo(tmp);
        }
        
        // user.dir should be restored after close
        assertThat(System.getProperty("user.dir")).isEqualTo(originalUserDir);
    }

    @Test
    void createDirDoesNotInitializeGit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            assertThat(repo.hasGit()).isFalse();
            assertThat(Files.exists(tmp.resolve(".git"))).isFalse();
        }
    }

    @Test
    void runCommandCapturesOutput(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Run status command (repo already has .git from createRepo)
            String output = repo.runCommand("status");
            
            assertThat(output).contains("LOCAL");
            assertThat(output).contains("REMOTE");
        }
    }

    @Test
    void writeAndReadFile(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("test.txt", "hello world");
            
            assertThat(repo.fileExists("test.txt")).isTrue();
            assertThat(repo.readFile("test.txt")).isEqualTo("hello world");
        }
    }

    @Test
    void writeFileInSubdirectory(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("sub/dir/file.txt", "content");
            
            assertThat(repo.fileExists("sub/dir/file.txt")).isTrue();
            assertThat(Files.exists(tmp.resolve("sub/dir"))).isTrue();
        }
    }

    @Test
    void gitAddAndCommit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("file.txt", "content");
            repo.gitAdd("file.txt");
            repo.gitCommit("Initial commit");
            
            // Verify commit was created
            try (var git = repo.getGit()) {
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
    void createSubdir(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            Path subdir = repo.createSubdir("src/main/java");
            
            assertThat(Files.exists(subdir)).isTrue();
            assertThat(Files.isDirectory(subdir)).isTrue();
        }
    }

    @Test
    void createVglInstance(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglCli vgl = repo.createVglInstance();
            
            assertThat(vgl).isNotNull();
            assertThat(vgl.getLocalDir()).isEqualTo(tmp.toString());
        }
    }

    @Test
    void multipleOperations(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Write files
            repo.writeFile("file1.txt", "content1");
            repo.writeFile("file2.txt", "content2");
            
            // Stage and commit
            repo.gitAdd("file1.txt");
            repo.gitAdd("file2.txt");
            repo.gitCommit("Add files");
            
            // Run VGL command
            String output = repo.runCommand("status");
            
            assertThat(output).contains("LOCAL");
            assertThat(repo.fileExists("file1.txt")).isTrue();
            assertThat(repo.fileExists("file2.txt")).isTrue();
        }
    }

    @Test
    void nestedTryWithResources(@TempDir Path tmp) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        
        Path repo1 = tmp.resolve("repo1");
        Path repo2 = tmp.resolve("repo2");
        Files.createDirectories(repo1);
        Files.createDirectories(repo2);
        
        try (VglTestHarness.VglTestRepo r1 = VglTestHarness.createRepo(repo1)) {
            assertThat(System.getProperty("user.dir")).isEqualTo(repo1.toString());
            assertThat(r1.getPath()).isEqualTo(repo1);
            
            try (VglTestHarness.VglTestRepo r2 = VglTestHarness.createRepo(repo2)) {
                assertThat(System.getProperty("user.dir")).isEqualTo(repo2.toString());
                assertThat(r2.getPath()).isEqualTo(repo2);
            }
            
            // After inner close, should be back to repo1
            assertThat(System.getProperty("user.dir")).isEqualTo(repo1.toString());
        }
        
        // After outer close, should be back to original
        assertThat(System.getProperty("user.dir")).isEqualTo(originalUserDir);
    }

    @Test
    void getGitFailsForDirWithoutGit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            assertThatThrownBy(() -> repo.getGit())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have .git initialized");
        }
    }

    @Test
    void gitConfigSetForTests(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            try (var git = repo.getGit()) {
                String email = git.getRepository().getConfig().getString("user", null, "email");
                String name = git.getRepository().getConfig().getString("user", null, "name");
                
                assertThat(email).isEqualTo("test@example.com");
                assertThat(name).isEqualTo("Test User");
            }
        }
    }

    @Test
    void useDirForCreateCommand(@TempDir Path tmp) throws Exception {
        // When testing the 'create' command, use createDir() not createRepo()
        // The create command initializes .git, so starting with an existing .git causes issues
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            assertThat(Files.exists(tmp.resolve(".git"))).isFalse();
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
            
            // Run create command - it will initialize .git and create .vgl
            String output = repo.runCommand("create", "-lr", tmp.toString());
            
            assertThat(output).contains("Created new local repository");
            assertThat(Files.exists(tmp.resolve(".git"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".gitignore"))).isTrue();
        }
    }

    @Test
    void useRepoForOtherCommands(@TempDir Path tmp) throws Exception {
        // When testing commands that need an existing repo, use createRepo()
        // This pre-initializes .git so commands can run immediately
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            assertThat(Files.exists(tmp.resolve(".git"))).isTrue();
            
            // Write and commit a file
            repo.writeFile("test.txt", "content");
            repo.gitAdd("test.txt");
            repo.gitCommit("initial");
            
            // Now test commands that operate on existing repos
            String statusOutput = repo.runCommand("status");
            assertThat(statusOutput).contains("LOCAL");
            
            // Modify file and test diff
            repo.writeFile("test.txt", "modified");
            String diffOutput = repo.runCommand("diff");
            assertThat(diffOutput).isNotEmpty();
        }
    }

    @Test
    void antiPatternCreateRepoThenCreate(@TempDir Path tmp) throws Exception {
        // ANTI-PATTERN: Don't use createRepo() followed by create command
        // This creates .git twice and causes the create command to fail
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // This will fail because:
            // 1. createRepo() already initialized .git
            // 2. create command without -lb flag finds .git exists
            // 3. Goes to Case 3: "Repository already exists" error
            
            String output = repo.runCommand("create", "-lr", tmp.toString());
            
            // Documents the problem - create fails when .git already exists
            assertThat(output).contains("Error: Repository already exists");
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse(); // .vgl not created
        }
    }
}
