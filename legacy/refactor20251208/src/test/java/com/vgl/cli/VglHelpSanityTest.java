package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

public class VglHelpSanityTest {
    @Test
    void vglHelpRuns(@TempDir Path tmp) throws Exception {
        String output = VglTestHarness.runVglCommand(tmp, "--help");
        assertThat(output).containsIgnoringCase("usage");
    }
}
