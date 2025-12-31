package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

public class VglStatusSanityTest {
    @Test
    void vglStatusRuns(@TempDir Path tmp) throws Exception {
                    // Run status -vv and parse output for summary and section counts
                    String output = VglTestHarness.runVglCommand(tmp, "status", "-vv");
                    String norm = output.replaceAll("(?m)^\\s+-- ","-- ");
                    String[] lines = norm.split("\r?\n");
                    int summaryUntracked = -1;
                    int summaryIgnored = -1;
                    for (String l : lines) {
                        if (l.contains("Undecided") && l.contains("Untracked") && l.contains("Ignored")) {
                            String s = l.trim();
                            String[] parts = s.split(",");
                            for (String part : parts) {
                                String p = part.trim();
                                if (p.endsWith("Untracked")) {
                                    summaryUntracked = Integer.parseInt(p.split(" ")[0]);
                                } else if (p.endsWith("Ignored")) {
                                    summaryIgnored = Integer.parseInt(p.split(" ")[0]);
                                }
                            }
                        }
                    }
                    int listedUntracked = 0;
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].startsWith("-- Untracked Files:")) {
                            int j = i+1;
                            while (j < lines.length && lines[j].startsWith("  ")) {
                                String entry = lines[j].trim();
                                if (!entry.equals("(none)")) listedUntracked++;
                                j++;
                            }
                            break;
                        }
                    }
                    int listedIgnored = 0;
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].startsWith("-- Ignored Files:")) {
                            int j = i+1;
                            while (j < lines.length && lines[j].startsWith("  ")) {
                                String entry = lines[j].trim();
                                if (!entry.equals("(none)")) listedIgnored++;
                                j++;
                            }
                            break;
                        }
                    }
                    assertThat(summaryUntracked).isGreaterThanOrEqualTo(0);
                    assertThat(summaryIgnored).isGreaterThanOrEqualTo(0);
                    assertThat(summaryUntracked).isEqualTo(listedUntracked);
                    assertThat(summaryIgnored).isEqualTo(listedIgnored);
            // Create nested repos
            Path nested0 = tmp.resolve("Repo0");
            Path nested1 = tmp.resolve("Repo1");
            java.nio.file.Files.createDirectories(nested0);
            java.nio.file.Files.createDirectories(nested1);
            org.eclipse.jgit.api.Git.init().setDirectory(nested0.toFile()).call();
            org.eclipse.jgit.api.Git.init().setDirectory(nested1.toFile()).call();
        // Create a repo
        VglTestHarness.runVglCommand(tmp, "create", "-lr", tmp.toString());
        // Add a file
        java.nio.file.Files.writeString(tmp.resolve("file.txt"), "content");
        VglTestHarness.runVglCommand(tmp, "track", "file.txt");
        VglTestHarness.runVglCommand(tmp, "commit", "Initial commit");
        // Run status with different verbosity
        String statusOutput = VglTestHarness.runVglCommand(tmp, "status");
        String statusVOutput = VglTestHarness.runVglCommand(tmp, "status", "-v");
        String statusVVOutput = VglTestHarness.runVglCommand(tmp, "status", "-vv");
        // Assertions
        assertThat(statusOutput).containsIgnoringCase("LOCAL");
        assertThat(statusVOutput).containsIgnoringCase("FILES");
        assertThat(statusVVOutput).containsIgnoringCase("Files to Commit");
    }
}
