package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Path;
 
import static org.assertj.core.api.Assertions.assertThat;

public class StatusNestedRepoTest {
    @Test
    void nestedReposAppearOnlyInIgnored(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // tracked file
            repo.writeFile("a.txt", "one");
            repo.gitAdd("a.txt");
            repo.gitCommit("initial");

            // untracked files
            repo.writeFile("b.txt", "two");
            repo.writeFile("c.txt", "three");
            repo.writeFile("x.java", "class X{} ");

            // nested repos
            Path nested0 = repo.getPath().resolve("Repo0");
            Path nested1 = repo.getPath().resolve("Repo1");
            java.nio.file.Files.createDirectories(nested0);
            java.nio.file.Files.createDirectories(nested1);
            Git.init().setDirectory(nested0.toFile()).call();
            Git.init().setDirectory(nested1.toFile()).call();

            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
            // ...existing code...

            // Use shared section header constants for robustness
            // HEADER_IGNORED is not used, so remove it
            String HEADER_UNTRACKED = com.vgl.cli.commands.helpers.StatusVerboseOutput.HEADER_UNTRACKED.trim();

            // nested repos must appear in Ignored with (repo) suffix
            assertThat(output).contains("Repo0/ (repo)");
            assertThat(output).contains("Repo1/ (repo)");

            // Ensure nested repos do not appear in Untracked list (use shared header)
            String norm = output.replaceAll("(?m)^\\s+-- ","-- ");
            assertThat(norm).doesNotContain(HEADER_UNTRACKED + "\n  Repo0");
            assertThat(norm).doesNotContain(HEADER_UNTRACKED + "\n  Repo1");

            // Parse the summary counts line and the Untracked listing to ensure counts match
            // Use normalized output (headers normalized) for parsing lists
            String[] lines = norm.split("\r?\n");
                // ...existing code...
            int summaryUntracked = -1;
            int summaryIgnored = -1;
            int summaryAdded = -1, summaryModified = -1, summaryRenamed = -1, summaryDeleted = -1;
            for (String l : lines) {
                String ltrim = l.trim();
                if (ltrim.contains("Added") && ltrim.contains("Modified") && ltrim.contains("Renamed") && ltrim.contains("Deleted")) {
                    // Parse summary counts for commit file types, robustly extract the first integer in each part
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
                } else if (l.contains("Undecided") && l.contains("Untracked") && l.contains("Ignored")) {
                    // Newer format may include leading "To Commit" and "To Merge" items.
                    String s = l.trim();
                    String[] parts = s.split(",");
                    // Find the parts containing the labels we care about rather than rely on fixed positions
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

            // Count actual untracked entries printed
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

            // If any summary variable is still -1, the summary line was not found or parsed
            if (summaryAdded == -1 || summaryModified == -1 || summaryRenamed == -1 || summaryDeleted == -1) {
                StringBuilder debug = new StringBuilder();
                debug.append("Summary line for Added/Modified/Renamed/Deleted not found or not parsed.\n");
                debug.append("Lines containing 'Added':\n");
                for (String l : lines) {
                    if (l.contains("Added")) debug.append(l).append("\n");
                }
                debug.append("\nFull output was:\n");
                debug.append(String.join("\n", lines));
                throw new AssertionError(debug.toString());
            }

            // Count actual added/modified/renamed/deleted files in verbose commit section
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

            // Count actual ignored repo entries
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

            // Assert summary counts match verbose commit file lists
            assertThat(summaryAdded).isEqualTo(listedAdded);
            assertThat(summaryModified).isEqualTo(listedModified);
            assertThat(summaryRenamed).isEqualTo(listedRenamed);
            assertThat(summaryDeleted).isEqualTo(listedDeleted);
        }
    }
}
