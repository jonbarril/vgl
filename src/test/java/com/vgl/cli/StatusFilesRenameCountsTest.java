package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusFilesRenameCountsTest {
    // ...existing code...

    @Test
    public void filesSummaryRenamedMatchesRCount(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // Create initial file and commit
            Files.writeString(repoDir.resolve("aa.txt"), "one");
            git.add().addFilepattern("aa.txt").call();
            git.commit().setMessage("add aa").call();

            // Commit a rename aa -> bb (should be detected as a commit-derived rename)
            Files.move(repoDir.resolve("aa.txt"), repoDir.resolve("bb.txt"));
            git.add().addFilepattern("bb.txt").call();
            git.rm().addFilepattern("aa.txt").call();
            git.commit().setMessage("rename aa->bb").call();

            // Perform a working-tree rename bb -> cc (uncommitted)
            Files.move(repoDir.resolve("bb.txt"), repoDir.resolve("cc.txt"));
            // Stage all changes at once to help JGit detect the rename
            git.add().addFilepattern(".").call();
            // Print JGit status after staging (only once, after add)
            org.eclipse.jgit.api.Status jgitStatus = git.status().call();
            System.out.println("[TEST-DEBUG] JGit status after staging:");
            System.out.println("  Added:    " + jgitStatus.getAdded());
            System.out.println("  Changed:  " + jgitStatus.getChanged());
            System.out.println("  Removed:  " + jgitStatus.getRemoved());
            System.out.println("  Missing:  " + jgitStatus.getMissing());
            System.out.println("  Modified: " + jgitStatus.getModified());
            System.out.println("  Untracked:" + jgitStatus.getUntracked());

            // Run status -vv and capture output (similar to other status tests)
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

            // Always print the full captured CLI output (including debug lines)
            System.out.println("[TEST-DEBUG-FULL-OUTPUT]\n" + out);

            // Debug: print filesToCommit-like info by parsing output
            System.out.println("[TEST-DEBUG] Parsed Files to Commit section:");
            boolean inFilesToCommit = false;
            for (String l : out.split("\\r?\\n")) {
                if (l.trim().equals("-- Files to Commit:")) inFilesToCommit = true;
                else if (l.startsWith("-- ") && inFilesToCommit) { inFilesToCommit = false; }
                else if (inFilesToCommit) System.out.println("  " + l);
            }

            // Count R entries across the entire output (commit-derived renames may appear
            // in commit/remote sections while working renames appear in working-tree sections)
            int rCount = 0;
            for (String l : out.split("\\r?\\n")) {
                String t = l.trim();
                if (t.isEmpty() || t.equals("(none)")) continue;
                String plain = t;
                if (plain.startsWith("R ")) rCount++;
            }

            // Extract FILES summary line and parse Renamed count
            int renamedSummary = -1;
            for (String l : out.split("\\r?\\n")) {
                if (l.startsWith("FILES")) {
                    // Example: FILES   1 Added, 0 Modified, 2 Renamed, 0 Deleted
                    String[] parts = l.split(",");
                    for (String p : parts) {
                        if (p.contains("Renamed")) {
                            String digits = p.trim().split(" ")[0];
                            try { renamedSummary = Integer.parseInt(digits); } catch (NumberFormatException e) { renamedSummary = -1; }
                        }
                    }
                    break;
                }
            }

            assertThat(renamedSummary).as("FILES line must contain a Renamed count").isGreaterThanOrEqualTo(0);
            assertThat(rCount).as("There should be at least one R entry in Files to Commit").isGreaterThan(0);
            assertThat(renamedSummary).as("Renamed count in FILES must equal number of R entries").isEqualTo(rCount);

            git.close();
        }
    }
}
