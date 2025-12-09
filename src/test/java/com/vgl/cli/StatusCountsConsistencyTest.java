package com.vgl.cli;

import com.vgl.cli.commands.status.StatusSyncFiles;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusCountsConsistencyTest {
    @Test
    public void filesCountsMatchListsAndLetters(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // Initial files and commit
            Files.writeString(repoDir.resolve("a.txt"), "one");
            Files.writeString(repoDir.resolve("b.txt"), "two");
            Files.writeString(repoDir.resolve("c.txt"), "three");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();

            // Create bare remote and push initial commit
            Path remoteBare = td.resolve("remote.git");
            Files.createDirectories(remoteBare);
            Git.init().setDirectory(remoteBare.toFile()).setBare(true).call();
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(remoteBare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();

            // Make working-tree changes
            // Modify a.txt (M)
            Files.writeString(repoDir.resolve("a.txt"), "one-modified");
            git.add().addFilepattern("a.txt").call();

            // Delete b.txt (D)
            Files.delete(repoDir.resolve("b.txt"));
            git.rm().addFilepattern("b.txt").call();

            // Add d.txt (A)
            Files.writeString(repoDir.resolve("d.txt"), "four");
            git.add().addFilepattern("d.txt").call();

            // Rename c.txt -> c_renamed.txt and commit so it appears in commit diffs (R)
            Files.move(repoDir.resolve("c.txt"), repoDir.resolve("c_renamed.txt"));
            git.add().addFilepattern("c_renamed.txt").call();
            git.rm().addFilepattern("c.txt").call();
            git.commit().setMessage("rename c to c_renamed").call();

            // Run vgl status -vv and capture output
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

            String out = baos.toString(StandardCharsets.UTF_8.name());

            // Parse FILES first line: "FILES   <Added> Added, <Modified> Modified, <Renamed> Renamed, <Deleted> Deleted"
            int added = -1, modified = -1, renamed = -1, deleted = -1;
            for (String l : out.split("\\r?\\n")) {
                if (l.startsWith("FILES")) {
                    String rest = l.substring("FILES".length()).trim();
                    // rest example: "0 Added, 0 Modified, 0 Renamed, 0 Deleted"
                    String[] parts = rest.split(",");
                    for (String p : parts) {
                        String t = p.trim();
                        if (t.endsWith("Added")) added = Integer.parseInt(t.split(" ")[0]);
                        if (t.endsWith("Modified")) modified = Integer.parseInt(t.split(" ")[0]);
                        if (t.endsWith("Renamed")) renamed = Integer.parseInt(t.split(" ")[0]);
                        if (t.endsWith("Deleted")) deleted = Integer.parseInt(t.split(" ")[0]);
                    }
                    break;
                }
            }

            // Verify counts match JGit status for added/modified/deleted and our commit-rename computation
            org.eclipse.jgit.api.Status status = git.status().call();
            int expectedAdded = status.getAdded().size();
            int expectedModified = status.getModified().size() + status.getChanged().size();
            int expectedDeleted = status.getRemoved().size() + status.getMissing().size();
            java.util.Set<String> commitRenames = StatusSyncFiles.computeCommitRenamedSet(git, status, "", "");
            java.util.Map<String,String> workingRenames = StatusSyncFiles.computeWorkingRenames(git);
            java.util.Set<String> unionRenames = new java.util.LinkedHashSet<>();
            if (commitRenames != null) unionRenames.addAll(commitRenames);
            if (workingRenames != null) unionRenames.addAll(workingRenames.values());
            int expectedRenamed = unionRenames.size();

            // Adjust expected added/deleted by removing any rename targets that appear in status added/removed
            int expectedAddedAdjusted = expectedAdded;
            int expectedDeletedAdjusted = expectedDeleted;
            for (String r : unionRenames) {
                if (status.getAdded().contains(r)) expectedAddedAdjusted = Math.max(0, expectedAddedAdjusted - 1);
                if (status.getRemoved().contains(r) || status.getMissing().contains(r)) expectedDeletedAdjusted = Math.max(0, expectedDeletedAdjusted - 1);
            }

            assertThat(added).as("Added count in FILES").isEqualTo(expectedAddedAdjusted);
            assertThat(modified).as("Modified count in FILES").isEqualTo(expectedModified);
            assertThat(deleted).as("Deleted count in FILES").isEqualTo(expectedDeletedAdjusted);
            assertThat(renamed).as("Renamed count in FILES").isEqualTo(expectedRenamed);

            // Verify Files to Commit letters include expected entries
            boolean inSection = false;
            java.util.Set<String> lines = new java.util.LinkedHashSet<>();
            for (String l : out.split("\\r?\\n")) {
                if (inSection) {
                    if (l.startsWith("  -- ")) break;
                    String t = l.trim();
                    if (t.isEmpty() || t.equals("(none)")) continue;
                    lines.add(t);
                }
                if (l.contains("-- Files to Commit:")) inSection = true;
            }

            boolean sawA = false, sawM = false, sawD = false, sawR = false;
            for (String ln : lines) {
                String plain = ln.replace("↑ ", "").replace("↓ ", "").replace("AHEAD ", "").replace("BEHIND ", "");
                if (plain.startsWith("A ") && plain.contains("d.txt")) sawA = true;
                if (plain.startsWith("M ") && plain.contains("a.txt")) sawM = true;
                if (plain.startsWith("D ") && plain.contains("b.txt")) sawD = true;
                if (plain.startsWith("R ") && plain.contains("c_renamed.txt")) sawR = true;
            }

            assertThat(sawA).isTrue();
            assertThat(sawM).isTrue();
            assertThat(sawD).isTrue();
            assertThat(sawR).isTrue();

            // Verify categorical second line counts match list section sizes
            // Find second line counts: "X Undecided, Y Tracked, Z Untracked, W Ignored"
            int undecidedCount = -1, trackedCount = -1, untrackedCount = -1, ignoredCount = -1;
            for (int i = 0; i < out.split("\\r?\\n").length; i++) {
                String l = out.split("\\r?\\n")[i];
                if (l.startsWith("FILES")) {
                    if (i + 1 < out.split("\\r?\\n").length) {
                        String second = out.split("\\r?\\n")[i + 1].trim();
                        String[] parts = second.split(",");
                        for (String p : parts) {
                            String t = p.trim();
                            if (t.endsWith("Undecided")) undecidedCount = Integer.parseInt(t.split(" ")[0]);
                            if (t.endsWith("Tracked")) trackedCount = Integer.parseInt(t.split(" ")[0]);
                            if (t.endsWith("Untracked")) untrackedCount = Integer.parseInt(t.split(" ")[0]);
                            if (t.endsWith("Ignored")) ignoredCount = Integer.parseInt(t.split(" ")[0]);
                        }
                    }
                    break;
                }
            }

            // Count entries under each detailed section
            java.util.Map<String, Integer> sectionCounts = new java.util.HashMap<>();
            String current = null;
            for (String l : out.split("\\r?\\n")) {
                if (l.startsWith("  -- ")) {
                    current = l.substring("  -- ".length()).replace(":", "").trim();
                    sectionCounts.put(current, 0);
                    continue;
                }
                if (current != null) {
                    String t = l.trim();
                    if (t.isEmpty() || t.equals("(none)")) continue;
                    // section entries are indented lines not starting with '--'
                    if (!t.startsWith("--") && !t.startsWith("FILES") && !t.startsWith("COMMITS") && !t.startsWith("LOCAL") && !t.startsWith("REMOTE")) {
                        sectionCounts.put(current, sectionCounts.getOrDefault(current, 0) + 1);
                    }
                }
            }

            assertThat(undecidedCount).isEqualTo(sectionCounts.getOrDefault("Undecided Files", 0));
            assertThat(trackedCount).isEqualTo(sectionCounts.getOrDefault("Tracked Files", 0));
            assertThat(untrackedCount).isEqualTo(sectionCounts.getOrDefault("Untracked Files", 0));
            assertThat(ignoredCount).isEqualTo(sectionCounts.getOrDefault("Ignored Files", 0));
        }
    }
}
