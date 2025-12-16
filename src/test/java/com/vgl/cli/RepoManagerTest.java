package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

class RepoManagerTest {

    @Test
    void createVglRepo_createsGitVglAndGitignore(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve("repo");
        String branch = "main";
        Properties props = new Properties();
        props.setProperty("custom.key", "customValue");

        // Git git = RepoManager.createVglRepo(repoDir, branch, props);
        assertThat(Files.exists(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".vgl"))).isTrue();
        assertThat(Files.exists(repoDir.resolve(".gitignore"))).isTrue();

        // .gitignore should contain .vgl
        String gi = Files.readString(repoDir.resolve(".gitignore"));
        assertThat(gi).contains(".vgl");

        // .vgl should contain custom property and local.dir
        Properties loaded = new Properties();
        try (var in = Files.newInputStream(repoDir.resolve(".vgl"))) {
            loaded.load(in);
        }
        assertThat(loaded.getProperty("custom.key")).isEqualTo("customValue");
        assertThat(loaded.getProperty("local.dir")).isEqualTo(repoDir.toAbsolutePath().normalize().toString());
        assertThat(loaded.getProperty("local.branch")).isEqualTo(branch);
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
