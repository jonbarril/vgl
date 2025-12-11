package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusTrackedFilesTest {
    @Test
    public void committedFilesAppearInTrackedSection(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        // Use VglTestHarness helpers to create git repo and vgl config
        VglTestHarness.createRepo(repoDir);
        java.nio.file.Files.writeString(repoDir.resolve("a.txt"), "hello");
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir.toFile())) {
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("add a").call();
        }
        String out = VglTestHarness.runVglCommand(repoDir, "status", "-vv");
        assertThat(out).contains("TRACKED");
        assertThat(out).contains("a.txt");
    }
}
