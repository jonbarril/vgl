package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: Ensures that when files are listed as modified in the verbose section,
 * the summary counts are nonzero and match the verbose file lists.
 */
public class StatusSummaryRegressionTest {
    @Test
    void summaryCountsMatchVerboseFileList(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Add and commit a file
            repo.writeFile("foo.txt", "one");
            repo.gitAdd("foo.txt");
            repo.gitCommit("initial");
            // Modify the file
            repo.writeFile("foo.txt", "two");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
            // Parse summary counts and verbose file lists
            String[] lines = output.split("\r?\n");
            int summaryAdded = -1, summaryModified = -1, summaryRenamed = -1, summaryDeleted = -1;
            for (String l : lines) {
                String ltrim = l.trim();
                if (ltrim.contains("Added") && ltrim.contains("Modified") && ltrim.contains("Renamed") && ltrim.contains("Deleted")) {
                    String[] parts = ltrim.split(",");
                    for (String part : parts) {
                        String p = part.trim();
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(p);
                        if (!m.find()) continue;
                        int num = Integer.parseInt(m.group(1));
                        if (p.endsWith("Added")) summaryAdded = num;
                        else if (p.endsWith("Modified")) summaryModified = num;
                        else if (p.endsWith("Renamed")) summaryRenamed = num;
                        else if (p.endsWith("Deleted")) summaryDeleted = num;
                    }
                }
            }
            // Count actual files in verbose commit section
            int listedAdded = 0, listedModified = 0, listedRenamed = 0, listedDeleted = 0;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("  -- Files to Commit:")) {
                    int j = i+1;
                    while (j < lines.length && lines[j].startsWith("  ")) {
                        String entry = lines[j].trim();
                        if (entry.equals("(none)")) break;
                        if (entry.startsWith("A ")) listedAdded++;
                        else if (entry.startsWith("M ")) listedModified++;
                        else if (entry.startsWith("R ")) listedRenamed++;
                        else if (entry.startsWith("D ")) listedDeleted++;
                        j++;
                    }
                    break;
                }
            }
            // Assert summary counts match verbose file lists
            assertThat(summaryAdded).isEqualTo(listedAdded);
            assertThat(summaryModified).isEqualTo(listedModified);
            assertThat(summaryRenamed).isEqualTo(listedRenamed);
            assertThat(summaryDeleted).isEqualTo(listedDeleted);
        }
    }
}
