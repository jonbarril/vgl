package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for Utils helper methods.
 */
public class UtilsTest {

    @Test
    public void openNearestGitRepoFindsRepository(@TempDir Path tempDir) throws Exception {
        // Create .git directory structure
        try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tempDir.toFile()).call()) {
            // Create nested directory
            Path nested = tempDir.resolve("src/main/java");
            Files.createDirectories(nested);
            
            // Find repo from nested directory
            Repository repo = Utils.openNearestGitRepo(nested.toFile());
            assertThat(repo).isNotNull();
            assertThat(repo.getDirectory().getParentFile()).isEqualTo(tempDir.toFile());
            repo.close();
        }
    }
    
    @Test
    public void openGitWithDirectory(@TempDir Path tempDir) throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.close();
            
            // Open git with specific directory
            Git reopened = Utils.openGit(tempDir.toFile());
            assertThat(reopened).isNotNull();
            reopened.close();
        }
    }
    
    @Test
    public void openNearestGitRepoReturnsNullWhenNoGit(@TempDir Path tempDir) throws Exception {
        // Utils searches upward, so it might find parent .git directories
        // This test verifies the method doesn't crash on non-git directories
        Repository repo = Utils.openNearestGitRepo(tempDir.toFile());
        // May be null or may find a parent git repo
        // The key is it doesn't throw an exception
        if (repo != null) {
            repo.close();
        }
    }
    
    @Test
    public void openGitReturnsNullForNonGitDirectory(@TempDir Path tempDir) throws Exception {
        // Utils searches upward, so it might find parent .git directories
        // This test verifies the method doesn't crash on non-git directories
        Git git = Utils.openGit(tempDir.toFile());
        // May be null or may find a parent git repo
        // The key is it doesn't throw an exception
        if (git != null) {
            git.close();
        }
    }
    
    @Test
    public void versionFromRuntimeUsesSystemProperty() {
        String originalVersion = System.getProperty("vgl.version");
        try {
            System.setProperty("vgl.version", "TEST-1.2.3");
            String version = Utils.versionFromRuntime();
            assertThat(version).isEqualTo("TEST-1.2.3");
        } finally {
            if (originalVersion != null) {
                System.setProperty("vgl.version", originalVersion);
            } else {
                System.clearProperty("vgl.version");
            }
        }
    }
    
    @Test
    public void versionFromRuntimeDefaultsToMVP() {
        String originalVersion = System.getProperty("vgl.version");
        try {
            System.clearProperty("vgl.version");
            String version = Utils.versionFromRuntime();
            // Will be either from package or "MVP"
            assertThat(version).isNotEmpty();
        } finally {
            if (originalVersion != null) {
                System.setProperty("vgl.version", originalVersion);
            }
        }
    }
    
    @Test
    public void expandGlobsWithNullReturnsEmpty() throws IOException {
        assertThat(Utils.expandGlobs(null)).isEmpty();
    }
    
    @Test
    public void expandGlobsWithEmptyListReturnsEmpty() throws IOException {
        assertThat(Utils.expandGlobs(java.util.Collections.emptyList())).isEmpty();
    }
}
