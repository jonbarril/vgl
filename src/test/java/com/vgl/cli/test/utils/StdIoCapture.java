package com.vgl.cli.test.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class StdIoCapture implements AutoCloseable {
    private final PrintStream originalOut;
    private final PrintStream originalErr;

    private final String priorNonInteractive;

    private final ByteArrayOutputStream out;
    private final ByteArrayOutputStream err;

    public StdIoCapture() {
        originalOut = System.out;
        originalErr = System.err;

        priorNonInteractive = System.getProperty("vgl.noninteractive");
        System.setProperty("vgl.noninteractive", "true");

        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();

        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    public String stdout() {
        return normalize(out.toString(StandardCharsets.UTF_8));
    }

    public String stderr() {
        return normalize(err.toString(StandardCharsets.UTF_8));
    }

    private static String normalize(String s) {
        String normalized = s.replace("\r\n", "\n");
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public void close() {
        System.setOut(originalOut);
        System.setErr(originalErr);

        if (priorNonInteractive == null) {
            System.clearProperty("vgl.noninteractive");
        } else {
            System.setProperty("vgl.noninteractive", priorNonInteractive);
        }
    }
}
