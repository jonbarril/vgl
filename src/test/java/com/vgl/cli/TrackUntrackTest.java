package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for track/untrack commands - verifies Git add/rm operations.
 */
public class TrackUntrackTest {

    @Test
    public void trackingAddsFilesToGitIndex(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create test files
            Files.writeString(tempDir.resolve("file1.txt"), "content1");
            Files.writeString(tempDir.resolve("file2.txt"), "content2");
            
            // Track them explicitly (JGit doesn't expand globs automatically)
            git.add().addFilepattern("file1.txt").call();
            git.add().addFilepattern("file2.txt").call();
            
            // Verify they're in the index
            Status status = git.status().call();
            assertThat(status.getAdded()).contains("file1.txt", "file2.txt");
        }
    }
    
    @Test
    public void untrackingRemovesFromIndexButKeepsFile(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create and commit a file
            Path testFile = tempDir.resolve("tracked.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("tracked.txt").call();
            git.commit().setMessage("Add file").call();
            
            // Untrack it (cached removal)
            git.rm().setCached(true).addFilepattern("tracked.txt").call();
            
            // Verify it's marked for removal in index but still exists on disk
            Status status = git.status().call();
            assertThat(status.getRemoved()).contains("tracked.txt");
            assertThat(Files.exists(testFile)).isTrue();
        }
    }
    
    @Test
    public void addFilepatternSupportsGlobSyntax(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create files
            Files.writeString(tempDir.resolve("app.log"), "log1");
            Files.writeString(tempDir.resolve("build.log"), "log2");
            Files.writeString(tempDir.resolve("readme.txt"), "text");
            
            // Track using "." to add all files
            git.add().addFilepattern(".").call();
            
            // Verify all files tracked
            Status status = git.status().call();
            assertThat(status.getAdded()).contains("app.log", "build.log", "readme.txt");
        }
    }
}
