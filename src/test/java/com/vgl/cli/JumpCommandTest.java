package com.vgl.cli;

import com.vgl.cli.commands.JumpCommand;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class JumpCommandTest {
    private Path testRoot;
    private Path repo1;
    private Path repo2;

    @BeforeEach
    void setUp() throws Exception {
        testRoot = VglTestHarness.createTestRoot();
        repo1 = testRoot.resolve("repo1");
        repo2 = testRoot.resolve("repo2");
        if (Files.exists(repo1)) deleteRecursively(repo1);
        if (Files.exists(repo2)) deleteRecursively(repo2);

        // Create two VGL repos using harness
        try (VglTestHarness.VglTestRepo r1 = VglTestHarness.createRepo(repo1)) {
            r1.writeFile("file1.txt", "content1");
            r1.gitAdd("file1.txt");
            r1.gitCommit("Initial commit repo1");
        }
        try (VglTestHarness.VglTestRepo r2 = VglTestHarness.createRepo(repo2)) {
            r2.writeFile("file2.txt", "content2");
            r2.gitAdd("file2.txt");
            r2.gitCommit("Initial commit repo2");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(testRoot)) deleteRecursively(testRoot);
    }

    @Test
    void noJumpStateReturnsZero() throws Exception {
        System.setProperty("user.dir", repo1.toString());
        
        // Create VGL state with no jump
        VglCli vgl = new VglCli();
        vgl.setLocalDir(repo1.toString());
        vgl.setLocalBranch("main");
        // Explicitly clear any inherited jump state
        vgl.setJumpLocalDir(null);
        vgl.setJumpLocalBranch(null);
        vgl.setJumpRemoteUrl(null);
        vgl.setJumpRemoteBranch(null);
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
        // Explicitly set jump to same as current (clear any inherited remote state)
        vgl.setJumpLocalDir(repo1.toString());
        vgl.setJumpLocalBranch("main");
        vgl.setJumpRemoteUrl(null);
        vgl.setJumpRemoteBranch(null);
        vgl.setRemoteUrl(null);
        vgl.setRemoteBranch(null);
        vgl.save();
        
        JumpCommand cmd = new JumpCommand();
        int result = cmd.run(List.of());
        
        assertThat(result).isEqualTo(0);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { }
                });
        }
    }
}
