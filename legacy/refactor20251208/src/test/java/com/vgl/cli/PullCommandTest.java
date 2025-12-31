package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for pull command behavior - verifies Git status detection logic.
 */
public class PullCommandTest {

    @Test
    public void detectsUncommittedChanges(@TempDir Path tempDir) throws Exception {
        // Create a repo with an uncommitted change
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "original");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Modify the file without committing
            Files.writeString(testFile, "modified");
            git.add().addFilepattern("test.txt").call();
            
            // Verify Git detects the change
            Status status = git.status().call();
            assertThat(status.getChanged()).contains("test.txt");
            assertThat(status.getChanged().isEmpty()).isFalse();
        }
    }
    
    @Test
    public void detectsCleanWorkingTree(@TempDir Path tempDir) throws Exception {
        // Create a repo with committed changes
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Verify working tree is clean
            Status status = git.status().call();
            assertThat(status.isClean()).isTrue();
            assertThat(status.getModified()).isEmpty();
            assertThat(status.getChanged()).isEmpty();
        }
    }
}
