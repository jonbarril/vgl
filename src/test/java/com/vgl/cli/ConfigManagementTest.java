package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Unit tests for VglCli config save/load functionality.
 * Tests cover: null values, same values, directory navigation, file search.
 */
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class ConfigManagementTest {

    private String oldUserHome;
    private Path tempUserHome;

    @BeforeEach
    void setUpHome() throws Exception {
        oldUserHome = System.getProperty("user.home");
        Path testBase = Path.of("build", "tmp", "config-management-test").toAbsolutePath();
        Files.createDirectories(testBase);
        tempUserHome = testBase.resolve("home");
        if (Files.exists(tempUserHome)) deleteRecursively(tempUserHome);
        Files.createDirectories(tempUserHome);
        System.setProperty("user.home", tempUserHome.toString());
    }

    @AfterEach
    void tearDownHome() throws Exception {
        if (oldUserHome != null) System.setProperty("user.home", oldUserHome); else System.clearProperty("user.home");
        if (tempUserHome != null && Files.exists(tempUserHome)) {
            try {
                Files.walk(tempUserHome)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignore) {}
                        });
            } catch (IOException ignore) {}
        }
    }

    private static void deleteRecursively(Path path) throws java.io.IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (java.io.IOException e) { }
                });
        }
    }

    @Test
    void saveAndLoadBasicConfig(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Create and configure VglCli
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.setRemoteUrl("https://example.com/repo.git");
            vgl.setRemoteBranch("develop");
            vgl.save();
            
            // Verify .vgl was created
            assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();
            
            // Load in new instance
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
            assertThat(vgl2.getRemoteUrl()).isEqualTo("https://example.com/repo.git");
            assertThat(vgl2.getRemoteBranch()).isEqualTo("develop");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void saveAndLoadWithNullRemote(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Create VglCli with null remote
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.setRemoteUrl(null);
            vgl.setRemoteBranch(null);
            vgl.save();
            
            // Load in new instance
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
            assertThat(vgl2.getRemoteUrl()).isNull();
            assertThat(vgl2.getRemoteBranch()).isNull();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void saveAndLoadWithJumpState(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Create VglCli with jump state
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.setJumpLocalDir(tmp.resolve("other").toString());
            vgl.setJumpLocalBranch("develop");
            vgl.setJumpRemoteUrl("https://jump.example.com/repo.git");
            vgl.setJumpRemoteBranch("feature");
            vgl.save();
            
            // Load in new instance
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getJumpLocalDir()).isEqualTo(tmp.resolve("other").toString());
            assertThat(vgl2.getJumpLocalBranch()).isEqualTo("develop");
            assertThat(vgl2.getJumpRemoteUrl()).isEqualTo("https://jump.example.com/repo.git");
            assertThat(vgl2.getJumpRemoteBranch()).isEqualTo("feature");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void saveAndLoadWithAllNullJumpState(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Create VglCli with all null jump state
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.setJumpLocalDir(null);
            vgl.setJumpLocalBranch(null);
            vgl.setJumpRemoteUrl(null);
            vgl.setJumpRemoteBranch(null);
            vgl.save();
            
            // Load in new instance
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getJumpLocalDir()).isNull();
            assertThat(vgl2.getJumpLocalBranch()).isNull();
            assertThat(vgl2.getJumpRemoteUrl()).isNull();
            assertThat(vgl2.getJumpRemoteBranch()).isNull();
            assertThat(vgl2.hasJumpState()).isFalse();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void findConfigFileInParentDirectory(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create .git and .vgl in parent
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            System.setProperty("user.dir", tmp.toString());
            
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.save();
            
            // Create subdirectory and change to it
            Path subdir = tmp.resolve("subdir");
            Files.createDirectories(subdir);
            System.setProperty("user.dir", subdir.toString());
            
            // VglCli should find .vgl in parent
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void findConfigFileInGrandparentDirectory(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create .git and .vgl in grandparent
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            System.setProperty("user.dir", tmp.toString());
            
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.save();
            
            // Create deep subdirectory and change to it
            Path deepDir = tmp.resolve("sub1/sub2/sub3");
            Files.createDirectories(deepDir);
            System.setProperty("user.dir", deepDir.toString());
            
            // VglCli should find .vgl in grandparent
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void doesNotSaveWithoutGitDirectory(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // No .git directory
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.save();
            
            // .vgl should NOT be created without .git
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void userDirPropertyAffectsFindConfigFile(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create repo1
            Path repo1 = tmp.resolve("repo1");
            Files.createDirectories(repo1);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(repo1.toFile()).call()) {
            }
            System.setProperty("user.dir", repo1.toString());
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(repo1.toString());
            vgl1.setLocalBranch("repo1-branch");
            vgl1.save();
            
            // Create repo2
            Path repo2 = tmp.resolve("repo2");
            Files.createDirectories(repo2);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(repo2.toFile()).call()) {
            }
            System.setProperty("user.dir", repo2.toString());
            
            VglCli vgl2 = new VglCli();
            vgl2.setLocalDir(repo2.toString());
            vgl2.setLocalBranch("repo2-branch");
            vgl2.save();
            
            // Switch back to repo1 via user.dir
            System.setProperty("user.dir", repo1.toString());
            VglCli vgl3 = new VglCli();
            assertThat(vgl3.getLocalBranch()).isEqualTo("repo1-branch");
            
            // Switch to repo2 via user.dir
            System.setProperty("user.dir", repo2.toString());
            VglCli vgl4 = new VglCli();
            assertThat(vgl4.getLocalBranch()).isEqualTo("repo2-branch");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void sameValuesStoredCorrectly(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Set local and jump to same values
            VglCli vgl = new VglCli();
            vgl.setLocalDir(tmp.toString());
            vgl.setLocalBranch("main");
            vgl.setRemoteUrl("https://example.com/repo.git");
            vgl.setRemoteBranch("main");
            vgl.setJumpLocalDir(tmp.toString());
            vgl.setJumpLocalBranch("main");
            vgl.setJumpRemoteUrl("https://example.com/repo.git");
            vgl.setJumpRemoteBranch("main");
            vgl.save();
            
            // Load and verify
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(vgl2.getJumpLocalDir());
            assertThat(vgl2.getLocalBranch()).isEqualTo(vgl2.getJumpLocalBranch());
            assertThat(vgl2.getRemoteUrl()).isEqualTo(vgl2.getJumpRemoteUrl());
            assertThat(vgl2.getRemoteBranch()).isEqualTo(vgl2.getJumpRemoteBranch());
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(30)
    void orphanedVglWithoutGitIsDeleted(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        try {
            System.setProperty("user.dir", tmp.toString());
            System.setErr(new PrintStream(errCapture));
            
            // Create .vgl without .git
            Path vglFile = tmp.resolve(".vgl");
            Files.writeString(vglFile, "local.dir=" + tmp + "\nlocal.branch=orphaned\n");
            
            assertThat(Files.exists(vglFile)).isTrue();
            
            // Force non-interactive mode and load should detect orphaned .vgl and delete it
            System.setProperty("vgl.noninteractive", "true");
            try {
                new VglCli();
            } finally {
                System.clearProperty("vgl.noninteractive");
            }
            
            // .vgl should be deleted
            assertThat(Files.exists(vglFile)).isFalse();
            
            // Should have printed warning
            String errOutput = errCapture.toString();
            assertThat(errOutput).contains("Warning: Found .vgl but no .git directory");
            assertThat(errOutput).contains("Deleted orphaned .vgl file");
        } finally {
            System.setProperty("user.dir", oldUserDir);
            System.setErr(oldErr);
        }
    }

    @Test
    void vglWithGitIsNotDeleted(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create both .git and .vgl
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            Path vglFile = tmp.resolve(".vgl");
            Files.writeString(vglFile, "local.dir=" + tmp + "\nlocal.branch=preserved\n");
            
            assertThat(Files.exists(vglFile)).isTrue();
            
            // Load should keep .vgl since .git exists
            VglCli vgl = new VglCli();
            
            // .vgl should still exist
            assertThat(Files.exists(vglFile)).isTrue();
            
            // Config should be loaded
            assertThat(vgl.getLocalBranch()).isEqualTo("preserved");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void missingVglWithGitUsesDefaults(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git but no .vgl
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
            
            // Load should use defaults
            VglCli vgl = new VglCli();
            
            assertThat(vgl.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl.getRemoteUrl()).isNull();
            assertThat(vgl.hasJumpState()).isFalse();
            
            // .vgl still shouldn't exist (not created on load)
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void noVglNoGitUsesDefaults(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Neither .vgl nor .git exist
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
            assertThat(Files.exists(tmp.resolve(".git"))).isFalse();
            
            // Load should use defaults
            VglCli vgl = new VglCli();
            
            assertThat(vgl.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl.getRemoteUrl()).isNull();
            assertThat(vgl.hasJumpState()).isFalse();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void updateExistingConfig(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Create .git and initial .vgl
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            // Update config
            VglCli vgl2 = new VglCli();
            vgl2.setLocalBranch("feature");
            vgl2.setRemoteUrl("https://new.example.com/repo.git");
            vgl2.save();
            
            // Load and verify updates
            VglCli vgl3 = new VglCli();
            assertThat(vgl3.getLocalBranch()).isEqualTo("feature");
            assertThat(vgl3.getRemoteUrl()).isEqualTo("https://new.example.com/repo.git");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void emptyStringVersusNull(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            // Test empty string is treated as null
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.setRemoteUrl("");  // Empty string
            vgl1.setRemoteBranch("");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getRemoteUrl()).isNull();  // Should be null, not empty
            assertThat(vgl2.getRemoteBranch()).isNull();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void whitespaceOnlyTreatedAsNull(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.setRemoteUrl("   ");  // Whitespace only
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            // Should handle whitespace gracefully
            String url = vgl2.getRemoteUrl();
            assertThat(url == null || url.trim().isEmpty()).isTrue();
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void relativePathsConvertedToAbsolute(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(".");  // Relative path
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            // Should be absolute, not "."
            assertThat(vgl2.getLocalDir()).isEqualTo(tmp.toString());
            assertThat(vgl2.getLocalDir()).doesNotContain(".");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void multipleInstancesShareConfigFile(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            // First instance creates config
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            // Second instance modifies
            VglCli vgl2 = new VglCli();
            vgl2.setRemoteUrl("https://example.com/repo.git");
            vgl2.save();
            
            // Third instance sees both changes
            VglCli vgl3 = new VglCli();
            assertThat(vgl3.getLocalBranch()).isEqualTo("main");
            assertThat(vgl3.getRemoteUrl()).isEqualTo("https://example.com/repo.git");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void configPathWithSpaces(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create directory with spaces
            Path dirWithSpaces = tmp.resolve("my project dir");
            Files.createDirectories(dirWithSpaces);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(dirWithSpaces.toFile()).call()) {
            }
            
            System.setProperty("user.dir", dirWithSpaces.toString());
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(dirWithSpaces.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(dirWithSpaces.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void configPathWithUnicode(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create directory with unicode characters
            Path unicodeDir = tmp.resolve("プロジェクト");
            Files.createDirectories(unicodeDir);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(unicodeDir.toFile()).call()) {
            }
            
            System.setProperty("user.dir", unicodeDir.toString());
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(unicodeDir.toString());
            vgl1.setLocalBranch("メイン");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(unicodeDir.toString());
            assertThat(vgl2.getLocalBranch()).isEqualTo("メイン");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void vglInSymlinkedDirectory(@TempDir Path tmp) throws Exception {
        // This test may not work on all systems (requires symlink permissions)
        String oldUserDir = System.getProperty("user.dir");
        try {
            Path realDir = tmp.resolve("real");
            Files.createDirectories(realDir);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(realDir.toFile()).call()) {
            }
            
            Path linkDir = tmp.resolve("link");
            try {
                Files.createSymbolicLink(linkDir, realDir);
            } catch (Exception e) {
                // Skip test if symlinks not supported
                return;
            }
            
            System.setProperty("user.dir", linkDir.toString());
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(linkDir.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            // Should work through symlink
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalBranch()).isEqualTo("main");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void longPathNames(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            // Create a very deep directory structure
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                pathBuilder.append("/very_long_directory_name_").append(i);
            }
            Path deepDir = tmp.resolve(pathBuilder.substring(1));
            Files.createDirectories(deepDir);
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(deepDir.toFile()).call()) {
            }
            
            System.setProperty("user.dir", deepDir.toString());
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(deepDir.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalDir()).isEqualTo(deepDir.toString());
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void specialCharactersInBranchName(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            try (@SuppressWarnings("unused") Git g = Git.init().setDirectory(tmp.toFile()).call()) {
            }
            
            // Branch names can have slashes, dashes, etc
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("feature/JIRA-1234_fix-bug");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getLocalBranch()).isEqualTo("feature/JIRA-1234_fix-bug");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void urlsWithSpecialCharacters(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            // URLs can have special characters, auth tokens, etc
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.setRemoteUrl("https://user:pass@github.com/org/repo.git");
            vgl1.save();
            
            VglCli vgl2 = new VglCli();
            assertThat(vgl2.getRemoteUrl()).isEqualTo("https://user:pass@github.com/org/repo.git");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void corruptedVglFileHandling(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        try {
            System.setProperty("user.dir", tmp.toString());
            System.setErr(new PrintStream(errCapture));
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            // Create corrupted .vgl file
            Path vglFile = tmp.resolve(".vgl");
            Files.writeString(vglFile, "this is not valid properties format\n!!!corrupted!!!");
            
            // Should handle gracefully (might print warning but not crash)
            VglCli vgl = new VglCli();
            
            // Should still work with defaults
            assertThat(vgl.getLocalDir()).isEqualTo(tmp.toString());
        } finally {
            System.setProperty("user.dir", oldUserDir);
            System.setErr(oldErr);
        }
    }

    @Test
    void vglFilePermissions(@TempDir Path tmp) throws Exception {
        String oldUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            Git.init().setDirectory(tmp.toFile()).call().close();
            
            VglCli vgl1 = new VglCli();
            vgl1.setLocalDir(tmp.toString());
            vgl1.setLocalBranch("main");
            vgl1.save();
            
            Path vglFile = tmp.resolve(".vgl");
            
            // Make read-only on non-Windows systems
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                vglFile.toFile().setReadOnly();
                
                // Try to save again - should handle permission error gracefully
                VglCli vgl2 = new VglCli();
                vgl2.setLocalBranch("feature");
                vgl2.save();  // Should not crash
            }
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }
}
