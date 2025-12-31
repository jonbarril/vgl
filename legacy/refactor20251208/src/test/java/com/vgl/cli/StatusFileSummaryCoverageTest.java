package com.vgl.cli;

import com.vgl.cli.test.utils.VglTestHarness;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

public class StatusFileSummaryCoverageTest {
    static Stream<String> changeTypes() {
        return Stream.of("added", "modified", "deleted", "renamed", "mixed");
    }

    @ParameterizedTest
    @MethodSource("changeTypes")
    void summaryCountsMatchVerboseForAllChangeTypes(String type, @TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Setup repo for each change type
            switch (type) {
                case "added":
                    repo.writeFile("a.txt", "one");
                    repo.gitAdd("a.txt");
                    break;
                case "modified":
                    repo.writeFile("a.txt", "one");
                    repo.gitAdd("a.txt");
                    repo.gitCommit("initial");
                    repo.writeFile("a.txt", "two");
                    break;
                case "deleted":
                    repo.writeFile("a.txt", "one");
                    repo.gitAdd("a.txt");
                    repo.gitCommit("initial");
                    java.nio.file.Files.deleteIfExists(repo.getPath().resolve("a.txt"));
                    break;
                case "renamed":
                    repo.writeFile("a.txt", "one");
                    repo.gitAdd("a.txt");
                    repo.gitCommit("initial");
                    java.nio.file.Files.move(repo.getPath().resolve("a.txt"), repo.getPath().resolve("b.txt"));
                    break;
                case "mixed":
                    repo.writeFile("a.txt", "one");
                    repo.gitAdd("a.txt");
                    repo.gitCommit("initial");
                    repo.writeFile("a.txt", "two"); // modified
                    repo.writeFile("b.txt", "new"); // added
                    repo.gitAdd("b.txt");
                    repo.writeFile("c.txt", "gone");
                    repo.gitAdd("c.txt");
                    repo.gitCommit("add c");
                    java.nio.file.Files.deleteIfExists(repo.getPath().resolve("c.txt")); // deleted
                    break;
            }
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-vv");
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
            assertThat(summaryAdded).isEqualTo(listedAdded);
            assertThat(summaryModified).isEqualTo(listedModified);
            assertThat(summaryRenamed).isEqualTo(listedRenamed);
            assertThat(summaryDeleted).isEqualTo(listedDeleted);
        }
    }
}
