package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for edge cases and validation scenarios.
 */
public class EdgeCasesTest {

    @Test
    public void emptyRepositoryHasNoCommits(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Empty repo has no HEAD
            assertThat(git.getRepository().resolve("HEAD")).isNull();
        }
    }
    
    @Test
    public void multipleRemotesConfiguration(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            
            // Configure multiple remotes
            config.setString("remote", "origin", "url", "https://github.com/user/repo1.git");
            config.setString("remote", "upstream", "url", "https://github.com/user/repo2.git");
            config.save();
            
            // Verify both are stored
            String origin = config.getString("remote", "origin", "url");
            String upstream = config.getString("remote", "upstream", "url");
            
            assertThat(origin).isEqualTo("https://github.com/user/repo1.git");
            assertThat(upstream).isEqualTo("https://github.com/user/repo2.git");
        }
    }
    
    @Test
    public void gitConfigUserSettings(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            
            // Set user configuration
            config.setString("user", null, "name", "Test User");
            config.setString("user", null, "email", "test@example.com");
            config.save();
            
            // Verify settings
            String name = config.getString("user", null, "name");
            String email = config.getString("user", null, "email");
            
            assertThat(name).isEqualTo("Test User");
            assertThat(email).isEqualTo("test@example.com");
        }
    }
    
    @Test
    public void fileWithSpacesInName(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create file with spaces
            Path fileWithSpaces = tempDir.resolve("file with spaces.txt");
            Files.writeString(fileWithSpaces, "content");
            
            // Add and commit
            git.add().addFilepattern("file with spaces.txt").call();
            var commit = git.commit().setMessage("Add file with spaces").call();
            
            assertThat(commit).isNotNull();
            assertThat(Files.exists(fileWithSpaces)).isTrue();
        }
    }
    
    @Test
    public void nestedDirectoryStructure(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create nested directories
            Path nested = tempDir.resolve("a/b/c/deep.txt");
            Files.createDirectories(nested.getParent());
            Files.writeString(nested, "deep content");
            
            // Add all recursively
            git.add().addFilepattern(".").call();
            var commit = git.commit().setMessage("Add nested structure").call();
            
            assertThat(commit).isNotNull();
        }
    }
}
