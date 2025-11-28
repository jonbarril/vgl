package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for push and remote commands - verifies Git remote configuration.
 */
public class PushAndRemoteTest {

    @Test
    public void remoteUrlCanBeConfigured(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Set remote URL
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", "https://github.com/test/repo.git");
            config.save();
            
            // Retrieve and verify
            String remoteUrl = config.getString("remote", "origin", "url");
            assertThat(remoteUrl).isEqualTo("https://github.com/test/repo.git");
        }
    }
    
    @Test
    public void remoteBranchCanBeConfigured(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Set remote tracking branch
            StoredConfig config = git.getRepository().getConfig();
            config.setString("branch", "main", "remote", "origin");
            config.setString("branch", "main", "merge", "refs/heads/main");
            config.save();
            
            // Verify configuration
            String remote = config.getString("branch", "main", "remote");
            String merge = config.getString("branch", "main", "merge");
            assertThat(remote).isEqualTo("origin");
            assertThat(merge).isEqualTo("refs/heads/main");
        }
    }
    
    @Test
    public void noRemoteReturnsNull(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Check for remote that doesn't exist
            StoredConfig config = git.getRepository().getConfig();
            String remoteUrl = config.getString("remote", "origin", "url");
            assertThat(remoteUrl).isNull();
        }
    }
    
    @Test
    public void currentBranchNameCanBeRetrieved(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "content");
            git.add().addFilepattern("test.txt").call();
            git.commit().setMessage("Initial").call();
            
            // Get current branch name
            String branch = git.getRepository().getBranch();
            
            // Default branch varies by Git version, but should not be null
            assertThat(branch).isNotNull();
            assertThat(branch).isIn("main", "master");
        }
    }
    
    @Test
    public void multipleRemotesCanBeConfigured(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            
            // Configure multiple remotes
            config.setString("remote", "origin", "url", "https://github.com/user/repo1.git");
            config.setString("remote", "upstream", "url", "https://github.com/user/repo2.git");
            config.save();
            
            // Verify both exist
            assertThat(config.getString("remote", "origin", "url"))
                .isEqualTo("https://github.com/user/repo1.git");
            assertThat(config.getString("remote", "upstream", "url"))
                .isEqualTo("https://github.com/user/repo2.git");
        }
    }
}
