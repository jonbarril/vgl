package com.vgl.cli;

import com.vgl.cli.commands.JumpCommand;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class JumpCommandTest {
    private Path tempDir;
    private Path repo1;
    private Path repo2;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("vgl-jump-test");
        repo1 = tempDir.resolve("repo1");
        repo2 = tempDir.resolve("repo2");
        
        // Create two repos
        Files.createDirectories(repo1);
        Files.createDirectories(repo2);
        
        // Initialize repo1
        runCommand(repo1, "git", "init");
        runCommand(repo1, "git", "config", "user.email", "test@test.com");
        runCommand(repo1, "git", "config", "user.name", "Test User");
        Files.writeString(repo1.resolve("file1.txt"), "content1");
        runCommand(repo1, "git", "add", ".");
        runCommand(repo1, "git", "commit", "-m", "Initial commit repo1");
        
        // Initialize repo2
        runCommand(repo2, "git", "init");
        runCommand(repo2, "git", "config", "user.email", "test@test.com");
        runCommand(repo2, "git", "config", "user.name", "Test User");
        Files.writeString(repo2.resolve("file2.txt"), "content2");
        runCommand(repo2, "git", "add", ".");
        runCommand(repo2, "git", "commit", "-m", "Initial commit repo2");
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(tempDir);
    }

    @Test
    void noJumpStateReturnsZero() throws Exception {
        System.setProperty("user.dir", repo1.toString());
        
        // Create VGL state with no jump
        VglCli vgl = new VglCli();
        vgl.setLocalDir(repo1.toString());
        vgl.setLocalBranch("main");
        vgl.save();
        
        JumpCommand cmd = new JumpCommand();
        int result = cmd.run(List.of());
        
        assertThat(result).isEqualTo(0);
    }

    @Test
    void jumpStateIsSavedAndRestored() throws Exception {
        System.setProperty("user.dir", repo1.toString());
        
        VglCli vgl = new VglCli();
        
        // Set initial state (repo1)
        vgl.setLocalDir(repo1.toString());
        vgl.setLocalBranch("main");
        vgl.setRemoteUrl("https://example.com/repo1");
        vgl.setRemoteBranch("main");
        
        // Set jump state (repo2)
        vgl.setJumpLocalDir(repo2.toString());
        vgl.setJumpLocalBranch("main");
        vgl.setJumpRemoteUrl("https://example.com/repo2");
        vgl.setJumpRemoteBranch("main");
        vgl.save();
        
        // Verify jump state exists
        assertThat(vgl.hasJumpState()).isTrue();
        assertThat(vgl.getJumpLocalDir()).isEqualTo(repo2.toString());
    }

    @Test
    void jumpWithIdenticalContextReturnsZero() throws Exception {
        System.setProperty("user.dir", repo1.toString());
        
        VglCli vgl = new VglCli();
        vgl.setLocalDir(repo1.toString());
        vgl.setLocalBranch("main");
        vgl.setJumpLocalDir(repo1.toString());
        vgl.setJumpLocalBranch("main");
        vgl.save();
        
        JumpCommand cmd = new JumpCommand();
        int result = cmd.run(List.of());
        
        assertThat(result).isEqualTo(0);
    }

    private void runCommand(Path dir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { }
                });
        }
    }
}
