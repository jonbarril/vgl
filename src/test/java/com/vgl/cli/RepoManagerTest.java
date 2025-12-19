package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.services.RepoManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class RepoManagerTest {


    @Test
    void createVglRepo_createsGitVglAndGitignore(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        String branch = "main";
        Properties props = new Properties();
        props.setProperty("custom.key", "customValue");

        RepoManager.createVglRepo(repoDir, branch, props);
        assertThat(Files.exists(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".vgl"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".gitignore"))).isTrue();

        String gi = Files.readString(repoDir.resolve(".gitignore"));
        assertThat(gi).contains(".vgl");

        Properties loaded = new Properties();
        try (var in = Files.newInputStream(repoDir.resolve(".vgl"))) {
            loaded.load(in);
        }
        assertThat(loaded.getProperty("custom.key")).isEqualTo("customValue");
        assertThat(loaded.getProperty("local.dir")).isEqualTo(repoDir.toAbsolutePath().normalize().toString());
        assertThat(loaded.getProperty("local.branch")).isEqualTo(branch);
    }

    @Test
    void createVglRepo_failsOnInvalidPath() {
        // Create a temp file and try to use it as a repo directory (should fail)
        try {
            Path tempFile = Files.createTempFile("not_a_dir", ".tmp");
            Properties props = new Properties();
            assertThatThrownBy(() -> RepoManager.createVglRepo(tempFile, "main", props))
                .isInstanceOf(IOException.class);
            Files.deleteIfExists(tempFile);
        } catch (Exception e) {
            // If temp file creation fails, test is not valid
            fail("Could not create temp file for invalid path test");
        }
    }

    @Test
    void updateVglConfig_failsOnPermissionDenied(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        RepoManager.createVglRepo(repoDir, "main", null);
        Path vglFile = repoDir.resolve(".vgl");
        vglFile.toFile().setReadOnly();
        Properties props = new Properties();
        props.setProperty("foo", "bar");
        assertThatThrownBy(() -> RepoManager.updateVglConfig(repoDir, props))
            .isInstanceOf(IOException.class);
        vglFile.toFile().setWritable(true);
    }

    @Test
    void createVglRepo_gitignoreAlreadyContainsVgl(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve(".gitignore"), ".vgl\notherstuff\n");
        RepoManager.createVglRepo(repoDir, "main", null);
        String gi = Files.readString(repoDir.resolve(".gitignore"));
        assertThat(gi.indexOf(".vgl")).isEqualTo(gi.lastIndexOf(".vgl"));
    }

    @Test
    void createVglRepo_handlesCorruptedVglFile(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve(".vgl"), "not a properties file\n\u0000\u0001\u0002");
        Properties props = new Properties();
        props.setProperty("foo", "bar");
        RepoManager.createVglRepo(repoDir, "main", props);
        Properties loaded = new Properties();
        try (var in = Files.newInputStream(repoDir.resolve(".vgl"))) {
            loaded.load(in);
        }
        assertThat(loaded.getProperty("foo")).isEqualTo("bar");
    }

    @Test
    void updateVglConfig_overwritesVglFile(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        // Git git = RepoManager.createVglRepo(repoDir, "main", null);
        Properties props = new Properties();
        props.setProperty("foo", "bar");
        RepoManager.updateVglConfig(repoDir, props);
        Properties loaded = new Properties();
        try (var in = Files.newInputStream(repoDir.resolve(".vgl"))) {
            loaded.load(in);
        }
        assertThat(loaded.getProperty("foo")).isEqualTo("bar");
    }

    @Test
    void createVglRepo_idempotentIfAlreadyExists(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        Git git1 = RepoManager.createVglRepo(repoDir, "main", null);
        Git git2 = RepoManager.createVglRepo(repoDir, "main", null);
        assertThat(Files.exists(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".vgl"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".gitignore"))).isTrue();
        assertThat(git1.getRepository().getDirectory()).isEqualTo(git2.getRepository().getDirectory());
    }
}
