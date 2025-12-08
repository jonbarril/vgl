package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TrackNoDebugOutputTest {

    @Test
    public void trackAllDoesNotPrintDebugMarkers(@TempDir Path td) throws Exception {
        Path repo = td.resolve("repo");
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            // create some files and an undecided entry in .vgl
            r.writeFile("aa.txt", "one");
            r.writeFile("subdir/q.txt", "two");
            // create a .vgl config so -all path is exercised
            String cfg = "local.dir=" + repo.toAbsolutePath().toString().replace('\\','/') + "\nlocal.branch=main\n";
            Files.writeString(repo.resolve(".vgl"), cfg);

            String out = r.runCommand("track", "-all");
            // Ensure no debug markers are printed
            assertThat(out).doesNotContain("[vgl.debug").doesNotContain("[vgl.debug:FORCE");
            // Output should mention Tracking or No undecided files (one of these is acceptable)
            assertThat(out).containsAnyOf("Tracking:", "No undecided files");
        }
    }
}
