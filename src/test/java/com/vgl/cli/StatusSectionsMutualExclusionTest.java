package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.utils.Utils;
public class StatusSectionsMutualExclusionTest {

    @Test
    public void sectionsAreMutuallyExclusive(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // Committed (tracked) files
            Files.writeString(repoDir.resolve("a.txt"), "a");
            Files.writeString(repoDir.resolve("b.txt"), "b");
            git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
            git.commit().setMessage("initial").call();

            // Untracked file which we'll mark as undecided via VglRepo.updateUndecidedFilesFromWorkingTree
            Files.writeString(repoDir.resolve("c.txt"), "c");

            // Nested repositories
            Path repo1 = repoDir.resolve("Repo1");
            Files.createDirectories(repo1);
            try (Git nested = Git.init().setDirectory(repo1.toFile()).call()) {
                // create nested commit so it is a repo
                Files.writeString(repo1.resolve("x.txt"), "x");
                nested.add().addFilepattern("x.txt").call();
                nested.commit().setMessage("nested").call();
            }
            Path repo2 = repoDir.resolve("Repo2");
            Files.createDirectories(repo2);
            try (Git nested2 = Git.init().setDirectory(repo2.toFile()).call()) {
                Files.writeString(repo2.resolve("y.txt"), "y");
                nested2.add().addFilepattern("y.txt").call();
                nested2.commit().setMessage("nested2").call();
            }

            // Capture status and write undecided (.vgl) via VglRepo
            org.eclipse.jgit.api.Status status = git.status().call();
            VglRepo vglRepo = Utils.findVglRepo(repoDir);
            if (vglRepo != null) {
                vglRepo.updateUndecidedFilesFromWorkingTree(git, status);
            }

            // Run CLI and capture output
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream oldOut = System.out;
            java.io.PrintStream oldErr = System.err;
            String oldUserDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", repoDir.toString());
                java.io.PrintStream ps = new java.io.PrintStream(baos, true, "UTF-8");
                System.setOut(ps);
                System.setErr(ps);
                new VglCli().run(new String[]{"status", "-vv"});
            } finally {
                System.setProperty("user.dir", oldUserDir);
                System.setOut(oldOut);
                System.setErr(oldErr);
            }

            String out = baos.toString("UTF-8");

            Set<String> undecided = extractSection(out, "-- Undecided Files:");
            Set<String> tracked = extractSection(out, "-- Tracked Files:");
            Set<String> untracked = extractSection(out, "-- Untracked Files:");
            Set<String> ignored = extractSection(out, "-- Ignored Files:");

            // Normalize entries: remove trailing spaces and " (repo)" marker
            Set<String> normUndecided = normalize(undecided);
            Set<String> normTracked = normalize(tracked);
            Set<String> normUntracked = normalize(untracked);
            Set<String> normIgnored = normalize(ignored);

            // Parse the categorical counts line (Undecided, Tracked, Untracked, Ignored)
            int parsedUndecided = -1, parsedTracked = -1, parsedUntracked = -1, parsedIgnored = -1;
            String[] lines = out.split("\\r?\\n");
            for (String l : lines) {
                if (l.contains("Undecided") && l.contains("Tracked") && l.contains("Ignored")) {
                    // Expected format: spaces then "<N> Undecided, <M> Tracked, <K> Untracked, <P> Ignored"
                    String[] parts = l.trim().split(",");
                    if (parts.length >= 4) {
                        parsedUndecided = Integer.parseInt(parts[0].trim().split(" ")[0]);
                        parsedTracked = Integer.parseInt(parts[1].trim().split(" ")[0]);
                        parsedUntracked = Integer.parseInt(parts[2].trim().split(" ")[0]);
                        parsedIgnored = Integer.parseInt(parts[3].trim().split(" ")[0]);
                    }
                    break;
                }
            }

            // Counts must match the sizes of the corresponding subsections
            assertThat(parsedUndecided).isEqualTo(normUndecided.size());
            assertThat(parsedTracked).isEqualTo(normTracked.size());
            assertThat(parsedUntracked).isEqualTo(normUntracked.size());
            assertThat(parsedIgnored).isEqualTo(normIgnored.size());

            // Ensure mutual exclusivity pairwise
            assertThat(intersection(normUndecided, normTracked)).isEmpty();
            assertThat(intersection(normUndecided, normUntracked)).isEmpty();
            assertThat(intersection(normUndecided, normIgnored)).isEmpty();
            assertThat(intersection(normTracked, normUntracked)).isEmpty();
            assertThat(intersection(normTracked, normIgnored)).isEmpty();
            assertThat(intersection(normUntracked, normIgnored)).isEmpty();

            // Ensure nested repos appear only in ignored
            assertThat(normIgnored).contains("Repo1/");
            assertThat(normIgnored).contains("Repo2/");
            assertThat(normTracked).doesNotContain("Repo1/");
            assertThat(normTracked).doesNotContain("Repo2/");
        }
    }

    private static Set<String> extractSection(String output, String header) {
        Set<String> out = new LinkedHashSet<>();
        String[] lines = output.split("\r?\n");
        boolean inSection = false;
        for (String l : lines) {
            if (inSection) {
                if (l.startsWith("  -- ")) break; // next section
                String t = l.trim();
                if (t.isEmpty()) continue;
                if (t.equals("(none)")) continue;
                out.add(t);
            }
            if (l.contains(header)) {
                inSection = true;
            }
        }
        return out;
    }

    private static Set<String> normalize(Set<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            String t = s.replace(" (repo)", "");
            out.add(t.trim());
        }
        return out;
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> c = new LinkedHashSet<>(a);
        c.retainAll(b);
        return c;
    }
}
