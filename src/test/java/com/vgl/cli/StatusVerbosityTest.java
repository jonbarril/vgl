package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.*;

public class StatusVerbosityTest {

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
    void statusShowsBasicSections() throws Exception {
        String output = run("status");
        
        // Basic status should show main sections
        assertThat(output).contains("LOCAL");
        assertThat(output).contains("REMOTE");
        assertThat(output).contains("STATE");
        assertThat(output).contains("FILES");
    }

    @Test
    void statusVerboseShowsCommitInfo() throws Exception {
        String output = run("status", "-v");
        
        // -v should show commit hashes (if there are commits)
        // or show (none) if no commits
        assertThat(output).containsAnyOf("  (none)", "[0-9a-f]{7}");
    }

    @Test
    void statusVeryVerboseShowsTrackedSection() throws Exception {
        String output = run("status", "-vv");
        
        // -vv should always show these sections
        assertThat(output).contains("-- Tracked Files:");
        assertThat(output).contains("-- Untracked Files:");
        assertThat(output).contains("-- Ignored Files:");
    }
}
