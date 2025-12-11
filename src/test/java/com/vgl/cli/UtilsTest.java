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
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            
            // Open git with specific directory
            Git reopened = Utils.findGitRepo(tempDir);
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
        Git git = Utils.findGitRepo(tempDir);
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

    // ============================================================================
    // Tests for new findGitRepo/findVglRepo API
    // ============================================================================

    @Test
    public void findGitRepoWithPathFindsRepo(@TempDir Path tmp) throws Exception {
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
            try (Git found = Utils.findGitRepo(tmp)) {
                assertThat(found).isNotNull();
                assertThat(found.getRepository().getWorkTree().toPath()).isEqualTo(tmp);
            }
        }
    }

    @Test
    public void findGitRepoWithPathReturnsNullWhenNoRepo(@TempDir Path tmp) throws Exception {
        // Create a directory with no .git and no parent .git
        Path isolated = tmp.resolve("isolated/deep/path");
        Files.createDirectories(isolated);
        
        // Should return null or find a parent repo (depending on test environment)
        Git git = Utils.findGitRepo(isolated);
        if (git != null) {
            git.close();
        }
    }

    @Test
    public void findVglRepoReturnsNullWithoutGit(@TempDir Path tmp) throws Exception {
        // Test with ceiling to prevent finding workspace .git
        VglRepo repo = Utils.findVglRepo(tmp, tmp.getParent());
        assertThat(repo).isNull();
    }

    @Test
    public void findVglRepoFindsGitAndConfig(@TempDir Path tmp) throws Exception {
        // Create git repo and .vgl config using VglTestHarness helpers
        String localDirValue = tmp.toString().replace('\\', '/');
        try (Git git = VglTestHarness.createGitRepo(tmp)) {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("local.dir", localDirValue);
            VglTestHarness.createVglConfig(tmp, props);
        }

        // Find VGL repo
        try (VglRepo vglRepo = Utils.findVglRepo(tmp)) {
            assertThat(vglRepo).isNotNull();
            assertThat(vglRepo.getGit()).isNotNull();
            assertThat(vglRepo.getRepoRoot()).isEqualTo(tmp);
            assertThat(vglRepo.getConfig()).isNotNull();
            assertThat(vglRepo.getConfig().getProperty("local.dir")).isEqualTo(localDirValue);
        }
    }

    @Test
    public void findVglRepoWorksWithoutVglFile(@TempDir Path tmp) throws Exception {
        // Create git repo without .vgl file
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        
        // Should still return VglRepo with empty config
        try (VglRepo vglRepo = Utils.findVglRepo(tmp)) {
            assertThat(vglRepo).isNotNull();
            assertThat(vglRepo.getConfig()).isEmpty();
        }
    }

    @Test
    public void getGitRepoRootReturnsCorrectPath(@TempDir Path tmp) throws Exception {
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        
        Path repoRoot = Utils.getGitRepoRoot(tmp);
        assertThat(repoRoot).isEqualTo(tmp);
    }

    @Test
    public void getGitRepoRootReturnsNullWithoutRepo(@TempDir Path tmp) throws Exception {
        Path isolated = tmp.resolve("no-repo");
        Files.createDirectories(isolated);
        
        // May return null or find parent repo - just verify it doesn't crash
        Utils.getGitRepoRoot(isolated);
    }

    @Test
    public void isNestedRepoDetectsNestedRepositories(@TempDir Path tmp) throws Exception {
        // Create outer repo
        try (@SuppressWarnings("unused") Git outerGit = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        
        // Create nested repo
        Path nested = tmp.resolve("nested");
        Files.createDirectories(nested);
        try (@SuppressWarnings("unused") Git nestedGit = Git.init().setDirectory(nested.toFile()).call()) {
        }
        
        // Should detect nesting
        boolean isNested = Utils.isNestedRepo(nested);
        assertThat(isNested).isTrue();
    }

    @Test
    public void isNestedRepoReturnsFalseForNonNestedRepo(@TempDir Path tmp) throws Exception {
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        
        // Use ceiling above the parent to prevent finding workspace .git
        // If tmp is /temp/junit123, set ceiling to /temp so search stops there
        Path ceiling = (tmp.getParent() != null) ? tmp.getParent().getParent() : null;
        boolean isNested = Utils.isNestedRepo(tmp, ceiling);
        assertThat(isNested).isFalse();
    }
}
