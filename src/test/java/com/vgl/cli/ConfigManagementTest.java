package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

public class ConfigManagementTest {

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
    void configSavedToCorrectDirectory(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Config should be in the repository directory
            Path vglFile = tmp.resolve(".vgl");
            assertThat(Files.exists(vglFile)).isTrue();
            
            String config = Files.readString(vglFile);
            assertThat(config).contains("local.dir");
            assertThat(config).contains("local.branch");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void localCommandUpdatesConfigCorrectly(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString(), "-b", "main");
        Files.writeString(tmp.resolve("test.txt"), "content");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            run("track", "test.txt");
            run("commit", "initial");
            
            // Create another branch
            run("create", tmp.toString(), "-b", "branch2");
            
            // Switch back to main
            run("local", tmp.toString(), "-b", "main");
            
            // Config should reflect main branch
            String config = Files.readString(tmp.resolve(".vgl"));
            assertThat(config).contains("local.branch=main");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void remoteCommandUpdatesConfigCorrectly(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Set remote
            run("remote", "https://github.com/test/repo.git", "-b", "develop");
            
            // Config should contain remote info
            String config = Files.readString(tmp.resolve(".vgl"));
            assertThat(config).contains("remote.url=https://github.com/test/repo.git");
            assertThat(config).contains("remote.branch=develop");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void statusReadsConfigCorrectly(@TempDir Path tmp) throws Exception {
        // Create repo with specific branch
        run("create", tmp.toString(), "-b", "mybranch");

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Status should show correct branch
            String output = run("status");
            assertThat(output).contains("mybranch");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }

    @Test
    void vglFileInGitignoreByDefault(@TempDir Path tmp) throws Exception {
        // Create repo
        run("create", tmp.toString());

        String old = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tmp.toString());
            
            // Check .gitignore contains .vgl
            String gitignore = Files.readString(tmp.resolve(".gitignore"));
            assertThat(gitignore).contains(".vgl");
            
        } finally {
            System.setProperty("user.dir", old);
        }
    }
}
