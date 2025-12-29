package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpCommandGoldenTest {
    private static final String TEST_VERSION = "TEST_VERSION";
    private static String priorVersion;

    private PrintStream originalOut;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        if (priorVersion == null) {
            priorVersion = System.getProperty("vgl.version");
        }
        System.setProperty("vgl.version", TEST_VERSION);

        originalOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    @AfterAll
    static void afterAll() {
        if (priorVersion == null) {
            System.clearProperty("vgl.version");
        } else {
            System.setProperty("vgl.version", priorVersion);
        }
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void help_defaultOutput_isStable() throws Exception {
        new HelpCommand().run(List.of());
        assertThat(normalize(out)).isEqualTo(normalize(loadResourceText("help.default.txt")));
    }

    @Test
    void help_vOutput_isStable() throws Exception {
        new HelpCommand().run(List.of("-v"));
        assertThat(normalize(out)).isEqualTo(normalize(loadResourceText("help.v.txt")));
    }

    @Test
    void help_vvOutput_isStable() throws Exception {
        new HelpCommand().run(List.of("-vv"));
        assertThat(normalize(out)).isEqualTo(normalize(loadResourceText("help.vv.txt")));
    }

    private static String normalize(ByteArrayOutputStream out) {
        return normalize(out.toString(StandardCharsets.UTF_8));
    }

    private static String normalize(String s) {
        // Keep exact text but normalize line endings for Windows/Linux.
        String normalized = s.replace("\r\n", "\n");
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }

        // Be tolerant of whether the capture includes the final println newline.
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String loadResourceText(String resourceName) {
        String path = "com/vgl/cli/commands/" + resourceName;
        try (var in = HelpCommandGoldenTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            byte[] bytes = in.readAllBytes();
            // If a tool accidentally wrote a UTF-8 BOM, ignore it.
            int offset = 0;
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                offset = 3;
            }
            return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
