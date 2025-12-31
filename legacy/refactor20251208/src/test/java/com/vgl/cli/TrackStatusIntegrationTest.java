package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TrackStatusIntegrationTest {

    @Test
    void trackThenStatusShowsConsistency(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Create files
            repo.writeFile("a.txt", "one");
            repo.writeFile("b.txt", "two");
            repo.writeFile("c.txt", "three");
            repo.writeFile("x.java", "class X {}\n");

            // Create nested repos that should be ignored
            Path r0 = repo.createSubdir("Repo0");
            Git.init().setDirectory(r0.toFile()).call();
            Path r1 = repo.createSubdir("Repo1");
            Git.init().setDirectory(r1.toFile()).call();

            // Run the track command similar to your repro
            String trackOut = repo.runCommand("track", "Repo*", "*.txt", "*.java");
            assertThat(trackOut).contains("Tracking:");

            // JGit-level check: files should be staged (added)
            try (var git = repo.getGit()) {
                var status = git.status().call();
                assertThat(status.getAdded()).contains("b.txt", "c.txt", "x.java");
            }

            // CLI-level check: status output should not show these files under Untracked
            String statusOut = repo.runCommand("status", "-vv");
            List<String> untracked = getStatusSectionLines(statusOut, "Untracked Files");
            assertThat(untracked).doesNotContain("b.txt", "c.txt", "x.java");
        }
    }

    private java.util.List<String> getStatusSectionLines(String statusOutput, String sectionName) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String marker = "-- " + sectionName + ":";
        String[] split = statusOutput.split("\r?\n");
        boolean inSection = false;
        for (String l : split) {
            if (l.startsWith("-- ") && inSection) break; // end of section
            if (inSection) {
                String t = l.trim();
                if (!t.isEmpty()) lines.add(t);
            }
            if (l.startsWith(marker)) {
                inSection = true;
            }
        }
        return lines;
    }
}
