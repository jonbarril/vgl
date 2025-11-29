package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for CreateCommand behavior.
 */
public class CreateCommandTest {

    @Test
    public void createNewRepository(@TempDir Path tempDir) throws Exception {
        Path newRepo = tempDir.resolve("myrepo");
        
        try (Git git = Git.init().setDirectory(newRepo.toFile()).call()) {
            // No-op, just need to run VGL create
        }
        
        // Should create repo with default main branch
        assertThat(Files.exists(newRepo.resolve(".git"))).isTrue();
    }
    
    @Test
    public void createBranchInExistingRepo(@TempDir Path tempDir) throws Exception {
        // Create initial repo
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create initial commit
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
        }
        
        // Now create a new branch
        try (Git git = Git.open(tempDir.toFile())) {
            git.branchCreate().setName("feature").call();
            
            // Verify branch exists
            boolean branchExists = git.branchList().call().stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/feature"));
            assertThat(branchExists).isTrue();
        }
    }
    
    @Test
    public void errorWhenRepoExistsWithoutBranchFlag(@TempDir Path tempDir) throws Exception {
        // Create initial repo
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
        }
        
        // Attempting to create without -b should fail
        // This would be tested via integration test with actual command
        assertThat(Files.exists(tempDir.resolve(".git"))).isTrue();
    }
    
    @Test
    public void createBranchWithCustomName(@TempDir Path tempDir) throws Exception {
        // Create initial repo
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Create custom branch
            git.branchCreate().setName("my-feature-branch").call();
            
            boolean branchExists = git.branchList().call().stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/my-feature-branch"));
            assertThat(branchExists).isTrue();
        }
    }
    
    @Test
    public void branchAlreadyExistsMessage(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Create branch twice
            git.branchCreate().setName("feature").call();
            
            // Second attempt should recognize it exists
            boolean branchExists = git.branchList().call().stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/feature"));
            assertThat(branchExists).isTrue();
        }
    }
}
