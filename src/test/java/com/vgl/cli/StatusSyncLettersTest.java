package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusSyncLettersTest {
    @Test
    public void filesToCommitShowAMDRLetters(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // Create initial files and commit them
            Files.writeString(repoDir.resolve("a.txt"), "one");
            Files.writeString(repoDir.resolve("b.txt"), "two");
            Files.writeString(repoDir.resolve("c.txt"), "three");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();

            // Create a bare remote and push the initial commit so we can create commits-to-push
            Path remoteBare = td.resolve("remote.git");
            Files.createDirectories(remoteBare);
            Git.init().setDirectory(remoteBare.toFile()).setBare(true).call();
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(remoteBare.toUri().toString())).call();
            git.push().setRemote("origin").setPushAll().call();

            // Modify a.txt (should show M when staged)
            Files.writeString(repoDir.resolve("a.txt"), "one-modified");
            git.add().addFilepattern("a.txt").call();

            // Delete b.txt (should show D) and stage the deletion
            Files.delete(repoDir.resolve("b.txt"));
            git.rm().addFilepattern("b.txt").call();

            // Add new file d.txt and stage it (should show A)
            Files.writeString(repoDir.resolve("d.txt"), "four");
            git.add().addFilepattern("d.txt").call();

            // Rename c.txt -> c_renamed.txt and commit the rename so it appears in commits-to-push
            Files.move(repoDir.resolve("c.txt"), repoDir.resolve("c_renamed.txt"));
            git.add().addFilepattern("c_renamed.txt").call();
            git.rm().addFilepattern("c.txt").call();
            git.commit().setMessage("rename c to c_renamed").call();

            // Run status -vv and capture output
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

            // (debug prints removed)

            // Extract Files to Commit section lines
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

            // We expect to see lines starting with letter + space + filename.
            // Accept optional arrow prefixes like "↑ " or "↓ ".
            boolean sawA = false, sawM = false, sawD = false, sawR = false;
            for (String ln : lines) {
                String plain = ln.replace("↑ ", "").replace("↓ ", "");
                if (plain.startsWith("A ") && plain.contains("d.txt")) sawA = true;
                if (plain.startsWith("M ") && plain.contains("a.txt")) sawM = true;
                if (plain.startsWith("D ") && plain.contains("b.txt")) sawD = true;
                if (plain.startsWith("R ") && (plain.contains("c_renamed.txt") || plain.contains("c.txt"))) sawR = true;
            }

            assertThat(sawA).as("Expected to see 'A d.txt' in Files to Commit").isTrue();
            assertThat(sawM).as("Expected to see 'M a.txt' in Files to Commit").isTrue();
            assertThat(sawD).as("Expected to see 'D b.txt' in Files to Commit").isTrue();
            assertThat(sawR).as("Expected to see 'R c_renamed.txt' in Files to Commit").isTrue();
        }
    }
}
