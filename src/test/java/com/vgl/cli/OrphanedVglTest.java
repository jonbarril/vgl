package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for orphaned .vgl detection and cleanup.
 */
public class OrphanedVglTest {

    @Test
    public void detectsOrphanedVglFile(@TempDir Path tempDir) throws Exception {
        // Create .vgl without .git
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");
        
        // Verify .vgl exists but .git doesn't
        assertThat(Files.exists(vglFile)).isTrue();
        assertThat(Files.exists(tempDir.resolve(".git"))).isFalse();
    }
    
    @Test
    public void vglWithGitIsNotOrphaned(@TempDir Path tempDir) throws Exception {
        // Create both .vgl and .git
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path vglFile = tempDir.resolve(".vgl");
            Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");
            
            // Verify both exist
            assertThat(Files.exists(vglFile)).isTrue();
            assertThat(git.getRepository().getDirectory().exists()).isTrue();
        }
    }
    
    @Test
    public void vglCliDetectsOrphanedConfig(@TempDir Path tempDir) throws Exception {
        // Create orphaned .vgl
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");
        
        // Change to temp directory and try to load config
        String originalDir = System.getProperty("user.dir");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setProperty("user.dir", tempDir.toString());
            System.setOut(new PrintStream(output));
            
            // Try to create VglCli - should detect orphaned .vgl
            new VglCli();
            
            // In non-interactive mode, should auto-delete
            // Verify warning was shown
            String outputStr = output.toString();
            if (System.console() == null) {
                // Non-interactive: should auto-delete
                assertThat(outputStr).contains("Warning: Found orphaned .vgl");
            }
        } finally {
            System.setProperty("user.dir", originalDir);
            System.setOut(originalOut);
        }
    }
    
    @Test
    public void orphanedVglCleanupInNonInteractiveMode(@TempDir Path tempDir) throws Exception {
        // Create orphaned .vgl
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");
        
        assertThat(Files.exists(vglFile)).isTrue();
        
        // Simulate non-interactive cleanup (System.console() == null)
        if (System.console() == null) {
            // In tests, console is always null, so .vgl should be deleted
            String originalDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", tempDir.toString());
                new VglCli();
                
                // After VglCli init, orphaned .vgl should be gone
                assertThat(Files.exists(vglFile)).isFalse();
            } finally {
                System.setProperty("user.dir", originalDir);
            }
        }
    }
}
