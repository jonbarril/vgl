package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import org.eclipse.jgit.api.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

public class StatusUndecidedVsUntrackedTest {
    @Test
    public void newFilesAppearAsUndecidedNotUntracked(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);
        // Create repo and commit one file
        try (Git git = VglTestHarness.createGitRepo(repoDir)) {
            Files.writeString(repoDir.resolve("tracked.txt"), "tracked");
            git.add().addFilepattern("tracked.txt").call();
            git.commit().setMessage("initial").call();
            // Create .vgl config
            java.util.Properties props = new java.util.Properties();
            props.setProperty("local.dir", repoDir.toString().replace('\\', '/'));
            VglTestHarness.createVglConfig(repoDir, props);
        }
        // Create a new file (not added to git)
        Path undecidedFile = repoDir.resolve("hello.txt");
        Files.writeString(undecidedFile, "undecided");
        // Run status -vv
        String out = VglTestHarness.runVglCommand(repoDir, "status", "-vv");
        // Should appear in Undecided, not Untracked
        assertThat(out).contains("-- Undecided Files:");
        assertThat(out).contains("  hello.txt");
        assertThat(out).doesNotContain("-- Untracked Files:\n  hello.txt");
    }
}
