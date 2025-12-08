package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusVglIgnoredTest {
    @Test
    void vglFileIsIgnoredAndCounted(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create .gitignore that ignores .vgl
            repo.writeFile(".gitignore", ".vgl\n");
            // Create the .vgl file (should be ignored)
            repo.writeFile(".vgl", "config=true\n");

            // Create two files and stage them so they appear as Added
            repo.writeFile("a.txt", "one");
            repo.writeFile("b.txt", "two");
            // Do not commit; these should be reported as Added by status

            String output = repo.runCommand("status", "-vv");

            // .vgl must appear in Ignored list
            assertThat(output).contains("-- Ignored Files:");
            assertThat(output).contains(".vgl");

            // Ensure .vgl does not appear in Untracked or Files to Commit
            assertThat(output).doesNotContain("-- Untracked Files:\n  .vgl");
            assertThat(output).doesNotContain("A .vgl");

            // Parse the summary line for counts and ensure Ignored >= 1
            String[] lines = output.split("\r?\n");
            int summaryIgnored = -1;
            for (String l : lines) {
                if (l.contains("Undecided") && l.contains("Untracked") && l.contains("Ignored")) {
                    String s = l.trim();
                    String[] parts = s.split(",");
                    for (String part : parts) {
                        String p = part.trim();
                        if (p.endsWith("Ignored")) {
                            summaryIgnored = Integer.parseInt(p.split(" ")[0]);
                        }
                    }
                    break;
                }
            }
            assertThat(summaryIgnored).isGreaterThanOrEqualTo(1);
        }
    }
}
