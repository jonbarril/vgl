package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import org.eclipse.jgit.api.Git;

public class RepoAndStatusTest {

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        try {
            PrintStream ps = new PrintStream(baos, true, "UTF-8");
            System.setOut(ps);
            System.setErr(ps);
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    @Test
    void createMakesGitAndVgl_butNoGitignore(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Ensure clean
            Files.deleteIfExists(tmp.resolve(".vgl"));
            Files.deleteIfExists(tmp.resolve(".gitignore"));

            String out = run("create", tmp.toString());
            assertThat(Files.exists(tmp.resolve(".git"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".gitignore"))).isFalse();
            assertThat(out).contains("Created new local repository");
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void localCreatesVglWhenMissing(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // init repo directly (simulate existing repo without .vgl)
            Git.init().setDirectory(tmp.toFile()).call().close();
            Files.deleteIfExists(tmp.resolve(".vgl"));

            // Run local to set repository -> should create .vgl
            String out = run("local", tmp.toString());
            assertThat(out).contains("Switched to local repository");
            assertThat(Files.exists(tmp.resolve(".vgl"))).isTrue();
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void remoteDoesNotPopulateVglWithRemoteUrl(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo via create command so .git exists
            run("create", tmp.toString());
            // Remove .vgl to simulate missing config
            Files.deleteIfExists(tmp.resolve(".vgl"));

            // Run remote - should not populate remote.url in the saved .vgl
            String out = run("remote", "https://example.com/repo.git");
            assertThat(out).contains("Set remote repository");

            // If .vgl exists, it should not contain the remote url property
            if (Files.exists(tmp.resolve(".vgl"))) {
                String vgl = Files.readString(tmp.resolve(".vgl"));
                assertThat(vgl).doesNotContain("remote.url");
            }
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusAndVerboseOutputs(@TempDir Path tmp) throws Exception {
        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());

            // Create repo and commit a tracked file
            run("create", tmp.toString());
            Path a = tmp.resolve("a.txt");
            Files.writeString(a, "hello\n");
            run("track", "a.txt");
            run("remote", "origin");
            run("commit", "initial");

            // Modify tracked file and add an untracked file
            Files.writeString(a, "hello\nworld\n", StandardOpenOption.APPEND);
            Path b = tmp.resolve("b.txt");
            Files.writeString(b, "untracked\n");

            String basic = run("status");
            assertThat(basic).contains("LOCAL");
            assertThat(basic).contains("REMOTE");
            assertThat(basic).contains("STATE");
            assertThat(basic).contains("FILES");

            String v = run("status", "-v");
            assertThat(v).contains("-- Recent Commits:");
            assertThat(v).contains("initial");

            String vv = run("status", "-vv");
            assertThat(vv).contains("-- Tracked Files:");
            assertThat(vv).contains("-- Untracked Files:");
            assertThat(vv).contains("? b.txt");
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
