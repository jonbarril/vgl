package com.vgl.cli;

import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.*;
import com.vgl.cli.test.utils.VglTestHarness;

public class DummyTest {
    @Test
    void dummyPasses() throws Exception {
        assertThat(1 + 1).isEqualTo(2);
        // Just reference the harness class
        assertThat(VglTestHarness.class).isNotNull();
        // Create a test repo (no CLI or Git commands yet)
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("vgl-dummy");
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            assertThat(repo.getPath()).isEqualTo(tmp);
            // Run a minimal CLI command
            String output = repo.runCommand("status");
            assertThat(output).containsIgnoringCase("status"); // Just check output is non-empty and CLI runs
        }
    }
}
