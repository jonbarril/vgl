package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

            String output = repo.runCommand("status", "-vv");

            // nested repos must appear in Ignored with (repo) suffix
            assertThat(output).contains("Repo0/ (repo)");
            assertThat(output).contains("Repo1/ (repo)");

            // Ensure nested repos do not appear in Untracked list
            assertThat(output).doesNotContain("-- Untracked Files:\n  Repo0");
            assertThat(output).doesNotContain("-- Untracked Files:\n  Repo1");

            // Parse the summary counts line and the Untracked listing to ensure counts match
            String[] lines = output.split("\r?\n");
            int summaryUntracked = -1;
            int summaryIgnored = -1;
            for (String l : lines) {
                if (l.contains("Undecided") && l.contains("Untracked") && l.contains("Ignored")) {
                    // format: <spaces> <num> Undecided, <num> Tracked, <num> Untracked, <num> Ignored
                    String s = l.trim();
                    String[] parts = s.split(",");
                    if (parts.length >= 4) {
                        String untrackedPart = parts[2].trim(); // e.g. "5 Untracked"
                        String ignoredPart = parts[3].trim(); // e.g. "4 Ignored"
                        summaryUntracked = Integer.parseInt(untrackedPart.split(" ")[0]);
                        summaryIgnored = Integer.parseInt(ignoredPart.split(" ")[0]);
                        break;
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
        }
    }
}
