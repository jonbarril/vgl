package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Files;
import java.nio.file.Path;

public class StatusEmptyDirTest {

    @Test
    void statusDoesNotCreateVglInEmptyDir(@TempDir Path tmp) throws Exception {
        VglTestHarness.createDir(tmp);
        String out = VglTestHarness.runVglCommand(tmp, "status");
        // Should print only the warning/hint and not create .vgl
        assertThat(out).contains("WARNING: No VGL repository found in this directory or any parent.");
        assertThat(out).contains("Hint: Run 'vgl create' to initialize a new repo here.");
        assertThat(out).doesNotContain("LOCAL");
        assertThat(out).doesNotContain("REMOTE");
        assertThat(out).doesNotContain("COMMITS");
        assertThat(out).doesNotContain("FILES");
        assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
    }

    @Test
    void statusWithGitButNoVglDoesNotCreateVgl(@TempDir Path tmp) throws Exception {
        VglTestHarness.createRepo(tmp);
        // Ensure .vgl not present initially
        Files.deleteIfExists(tmp.resolve(".vgl"));
        String out = VglTestHarness.runVglCommand(tmp, "status");
        // Should print only the warning/hint and not create .vgl
        assertThat(out).contains("WARNING: No VGL repository found in this directory or any parent.");
        assertThat(out).contains("Hint: Run 'vgl create' to initialize a new repo here.");
        assertThat(out).doesNotContain("LOCAL");
        assertThat(out).doesNotContain("REMOTE");
        assertThat(out).doesNotContain("COMMITS");
        assertThat(out).doesNotContain("FILES");
        assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
    }

    @Test
    void orphanedVglIsRemovedInNonInteractiveMode(@TempDir Path tmp) throws Exception {
        VglTestHarness.createDir(tmp);
        // Create an orphaned .vgl (no .git in this dir)
        Files.writeString(tmp.resolve(".vgl"), "local.dir=" + tmp.toString());
        assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();

        String out = VglTestHarness.runVglCommand(tmp, "status");

        // LoadConfig should detect orphaned .vgl and delete it in non-interactive tests
        assertThat(out).contains("Found .vgl but no .git directory");
        assertThat(Files.exists(tmp.resolve(".vgl"))).isFalse();
    }
}
