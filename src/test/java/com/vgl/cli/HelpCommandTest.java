package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class HelpCommandTest {

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, "UTF-8"));
            new VglCli().run(args);
            return baos.toString("UTF-8");
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    @Timeout(30)
    void defaultPrintsCommandsAndVersion() throws Exception {
        System.setProperty("vgl.version", "TEST-VERSION");
        String out = run();
        assertThat(out).contains("Voodoo Gitless");
        assertThat(out).contains("TEST-VERSION");
        assertThat(out).contains("Commands:");
        assertThat(out).contains("create");
        System.clearProperty("vgl.version");
    }

    @Test
    @Timeout(30)
    void verboseIncludesFlagsSection() throws Exception {
        String out = run("help", "-v");
        assertThat(out).contains("Flags:");
        assertThat(out).contains("-noop");
        assertThat(out).contains("Glob Patterns:");
    }

    @Test
    @Timeout(30)
    void veryVerboseIncludesOverview() throws Exception {
        String out = run("help", "-vv");
        assertThat(out).contains("Overview:");
        assertThat(out).contains("Working Locally:");
        assertThat(out).contains("Use 'create -lr DIR' to make a new repository");
        assertThat(out).contains("Inspecting Your Work:");
    }
}
