package com.vgl.cli;


import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;

public class HelpCommandTest {

    @Test
    @Timeout(30)
    void defaultPrintsCommandsAndVersion(@TempDir Path tmp) throws Exception {
        // Set version property via environment or config if needed
        String out = VglTestHarness.runVglCommand(tmp, "help");
        assertThat(out).contains("Voodoo Gitless");
        assertThat(out).contains("Commands:");
        assertThat(out).contains("create");
    }

    @Test
    @Timeout(30)
    void verboseIncludesFlagsSection(@TempDir Path tmp) throws Exception {
        String out = VglTestHarness.runVglCommand(tmp, "help", "-v");
        assertThat(out).contains("Flags:");
        assertThat(out).contains("-noop");
        assertThat(out).contains("Glob Patterns:");
    }

    @Test
    @Timeout(30)
    void veryVerboseIncludesOverview(@TempDir Path tmp) throws Exception {
        String out = VglTestHarness.runVglCommand(tmp, "help", "-vv");
        assertThat(out).contains("Overview:");
        assertThat(out).contains("Working Locally:");
        assertThat(out).contains("Use 'create -lr DIR' to make a new repository");
        assertThat(out).contains("Inspecting Your Work:");
    }
}
