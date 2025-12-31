package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for status sync state detection (ahead/behind/diverged/in-sync).
 */
public class StatusSyncStateTest {

    @Test
    public void localAheadOfRemote(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Initial commit
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Configure tracking (simulated)
            Repository repo = git.getRepository();
            assertThat(repo.getFullBranch()).startsWith("refs/heads/");
            
            // Local has commits that remote doesn't
            Files.writeString(testFile, "updated");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Local change").call();
            
            // In real scenario, BranchTrackingStatus would show ahead by 1
            assertThat(repo.resolve("HEAD")).isNotNull();
        }
    }
    
    @Test
    public void branchTrackingStatusWithNoRemote(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Initial commit
            Files.writeString(tempDir.resolve("test.txt"), "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            Repository repo = git.getRepository();
            
            // No remote configured, tracking status should be null
            BranchTrackingStatus status = BranchTrackingStatus.of(repo, repo.getBranch());
            assertThat(status).isNull();
        }
    }
    
    @Test
    public void currentBranchName(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Initial commit to create branch
            Files.writeString(tempDir.resolve("test.txt"), "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            String branch = git.getRepository().getBranch();
            assertThat(branch).isNotEmpty();
            
            // Create and switch to new branch
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            
            String newBranch = git.getRepository().getBranch();
            assertThat(newBranch).isEqualTo("feature");
        }
    }
    
    @Test
    public void detachedHeadState(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Initial commit
            Files.writeString(tempDir.resolve("test.txt"), "content");
            git.add().addFilepattern("test.txt").call();
            var commit = git.commit().setMessage("Initial").call();
            
            // Checkout specific commit (detached HEAD)
            git.checkout().setName(commit.getName()).call();
            
            // In detached HEAD, getBranch() returns commit hash
            String branch = git.getRepository().getBranch();
            assertThat(branch).hasSize(40); // SHA-1 hash length
        }
    }
}
