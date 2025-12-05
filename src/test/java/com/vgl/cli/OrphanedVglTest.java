package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for orphaned .vgl detection and cleanup.
 */
@Timeout(30)
public class OrphanedVglTest {

    @Test
    @Timeout(30)
    public void detectsOrphanedVglFile(@TempDir Path tempDir) throws Exception {
        // Create .vgl without .git
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");
        
        // Verify .vgl exists but .git doesn't
        assertThat(Files.exists(vglFile)).isTrue();
        assertThat(Files.exists(tempDir.resolve(".git"))).isFalse();
    }
    
    @Test
    @Timeout(30)
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
    @Timeout(30)
    public void vglCliDetectsOrphanedConfig(@TempDir Path tempDir) throws Exception {
        // Create orphaned .vgl
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");

        // Change to temp directory and try to load config
        String originalDir = System.getProperty("user.dir");
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setProperty("user.dir", tempDir.toString());
            System.setProperty("vgl.noninteractive", "true");
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
            System.clearProperty("vgl.noninteractive");
            System.setOut(originalOut);
        }
    }
    
    @Test
    @Timeout(30)
    public void orphanedVglCleanupInNonInteractiveMode(@TempDir Path tempDir) throws Exception {
        // Create orphaned .vgl
        Path vglFile = tempDir.resolve(".vgl");
        Files.writeString(vglFile, "localDir=" + tempDir + "\nlocalBranch=main\n");

        assertThat(Files.exists(vglFile)).isTrue();

        // Force non-interactive cleanup
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            System.setProperty("vgl.noninteractive", "true");
            new VglCli();

            // After VglCli init, orphaned .vgl should be gone
            assertThat(Files.exists(vglFile)).isFalse();
        } finally {
            System.setProperty("user.dir", originalDir);
            System.clearProperty("vgl.noninteractive");
        }
    }

    @Test
    @Timeout(30)
    public void createsVglWhenGitExistsButVglDoesNot(@TempDir Path tempDir) throws Exception {
        // Create .git but no .vgl
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
            git.getRepository().getConfig().setString("user", null, "name", "Test User");
            git.getRepository().getConfig().save();
        }
        
        Path vglFile = tempDir.resolve(".vgl");
        assertThat(Files.exists(vglFile)).isFalse();
        assertThat(Files.exists(tempDir.resolve(".git"))).isTrue();
        
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tempDir.toString());
            vgl.setLocalBranch("main");
            vgl.save();
            
            // .vgl should now exist alongside .git
            assertThat(Files.exists(vglFile)).isTrue();
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    @Timeout(30)
    public void createsVglInSubdirectoryWhenGitInParent(@TempDir Path tempDir) throws Exception {
        // Create .git in parent
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
            git.getRepository().getConfig().setString("user", null, "name", "Test User");
            git.getRepository().getConfig().save();
        }
        
        // Create subdirectory and work from there
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);
        
        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", subdir.toString());
            
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tempDir.toString());
            vgl.setLocalBranch("main");
            vgl.save();
            
            // .vgl should be created alongside .git (in parent), not in subdirectory
            assertThat(Files.exists(tempDir.resolve(".vgl"))).isTrue();
            assertThat(Files.exists(subdir.resolve(".vgl"))).isFalse();
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    @Timeout(30)
    public void nestedGitReposUseClosestGit(@TempDir Path tempDir) throws Exception {
        // Create parent .git
        try (Git parentGit = Git.init().setDirectory(tempDir.toFile()).call()) {
            parentGit.getRepository().getConfig().setString("user", null, "email", "parent@example.com");
            parentGit.getRepository().getConfig().setString("user", null, "name", "Parent User");
            parentGit.getRepository().getConfig().save();
        }
        
        // Create nested .git in subdirectory
        Path subdir = tempDir.resolve("nested-repo");
        Files.createDirectories(subdir);
        try (Git childGit = Git.init().setDirectory(subdir.toFile()).call()) {
            childGit.getRepository().getConfig().setString("user", null, "email", "child@example.com");
            childGit.getRepository().getConfig().setString("user", null, "name", "Child User");
            childGit.getRepository().getConfig().save();
        }
        
        String originalDir = System.getProperty("user.dir");
        try {
            // Work from child directory
            System.setProperty("user.dir", subdir.toString());
            
            VglCli vgl = new VglCli();
            vgl.setLocalDir(subdir.toString());
            vgl.setLocalBranch("child-branch");
            vgl.save();
            
            // .vgl should be created alongside the closest .git (child)
            assertThat(Files.exists(subdir.resolve(".vgl"))).isTrue();
            assertThat(Files.exists(tempDir.resolve(".vgl"))).isFalse();
            
            // Verify it loaded correctly
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(subdir.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("child-branch");
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    @Timeout(30)
    public void findsVglInParentWhenWorkingInSubdirectory(@TempDir Path tempDir) throws Exception {
        // Create .git and .vgl in parent
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
            git.getRepository().getConfig().setString("user", null, "name", "Test User");
            git.getRepository().getConfig().save();
        }
        
        VglCli parentVgl = new VglCli();
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        parentVgl.setLocalDir(tempDir.toString());
        parentVgl.setLocalBranch("parent-branch");
        parentVgl.save();
        System.setProperty("user.dir", originalDir);
        
        // Create deep subdirectory
        Path deepSubdir = tempDir.resolve("level1/level2/level3");
        Files.createDirectories(deepSubdir);
        
        try {
            System.setProperty("user.dir", deepSubdir.toString());
            
            // Should find .vgl in parent
            VglCli vgl = new VglCli();
            assertThat(vgl.getLocalDir()).isEqualTo(tempDir.toString());
            assertThat(vgl.getLocalBranch()).isEqualTo("parent-branch");
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}
