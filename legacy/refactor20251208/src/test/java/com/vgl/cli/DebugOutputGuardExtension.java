package com.vgl.cli;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * JUnit 5 extension that fails a test if stdout/stderr contains VGL debug markers.
 * Registered via ServiceLoader so it applies to all tests.
 */
public class DebugOutputGuardExtension implements BeforeEachCallback, AfterEachCallback {
    private static final String DEBUG_MARKER = "[vgl.debug";
    private static final String DEBUG_MARKER_FORCE = "[vgl.debug:FORCE]";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
        ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
        PrintStream psOut = new PrintStream(baosOut, true, "UTF-8");
        PrintStream psErr = new PrintStream(baosErr, true, "UTF-8");
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create(DebugOutputGuardExtension.class));
        store.put("oldOut", System.out);
        store.put("oldErr", System.err);
        store.put("baosOut", baosOut);
        store.put("baosErr", baosErr);
        // Replace global System.out/err
        System.setOut(psOut);
        System.setErr(psErr);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create(DebugOutputGuardExtension.class));
        PrintStream oldOut = store.remove("oldOut", PrintStream.class);
        PrintStream oldErr = store.remove("oldErr", PrintStream.class);
        ByteArrayOutputStream baosOut = store.remove("baosOut", ByteArrayOutputStream.class);
        ByteArrayOutputStream baosErr = store.remove("baosErr", ByteArrayOutputStream.class);

        // Restore originals
        if (oldOut != null) System.setOut(oldOut);
        if (oldErr != null) System.setErr(oldErr);

        String out = "";
        try {
            if (baosOut != null) out += baosOut.toString("UTF-8");
        } catch (Exception ignore) {}
        try {
            if (baosErr != null) out += baosErr.toString("UTF-8");
        } catch (Exception ignore) {}

        if (out.contains(DEBUG_MARKER) || out.contains(DEBUG_MARKER_FORCE)) {
            // Fail the test with the captured output to make the reason visible
            throw new AssertionError("Test emitted VGL debug output; tests must not print debug markers.\nCaptured output:\n" + out);
        }
    }
}
