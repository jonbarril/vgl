package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class StatusEmptyDirTest {

    @Test
    void statusDoesNotCreateVglInEmptyDir(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            String out = repo.runCommand("status");
            // Should report no local repository and not create .vgl
            assertThat(out).contains("LOCAL  (none)");
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
        }
    }

    @Test
    void statusWithGitButNoVglDoesNotCreateVgl(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Ensure .vgl not present initially
            Files.deleteIfExists(tmp.resolve(".vgl"));
            String out = repo.runCommand("status");
            assertThat(out).contains("LOCAL");
            // Status should be passive and not create .vgl
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
        }
    }

    @Test
    void orphanedVglIsRemovedInNonInteractiveMode(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            // Create an orphaned .vgl (no .git in this dir)
            Files.writeString(tmp.resolve(".vgl"), "local.dir=" + tmp.toString());
            assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();

            String out = repo.runCommand("status");

            // LoadConfig should detect orphaned .vgl and delete it in non-interactive tests
            assertThat(out).contains("Found .vgl but no .git directory");
            assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
        }
    }
}
