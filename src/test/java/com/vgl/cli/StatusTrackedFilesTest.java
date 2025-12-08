package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusTrackedFilesTest {
    @Test
    public void committedFilesAppearInTrackedSection(@TempDir Path td) throws Exception {
        Path repoDir = td.resolve("repo");
        Files.createDirectories(repoDir);

        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // create files
            Files.writeString(repoDir.resolve("a.txt"), "hello");
            Files.writeString(repoDir.resolve("b.txt"), "world");
            Files.writeString(repoDir.resolve(".vgl"), "# vgl config");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();

            // run the CLI via VglCli.run in the repo directory
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
                String out = baos.toString("UTF-8");

                // ensure tracked section lists a.txt and b.txt (and does not list .vgl)
                assertThat(out).contains("-- Tracked Files:");
                assertThat(out).contains("a.txt");
                assertThat(out).contains("b.txt");
                assertThat(out).doesNotContain(".vgl (See");
            } finally {
                System.setProperty("user.dir", oldUserDir);
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
        }
    }
}
