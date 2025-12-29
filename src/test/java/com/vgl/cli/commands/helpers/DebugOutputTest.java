package com.vgl.cli.commands.helpers;

import org.junit.jupiter.api.Test;

public class DebugOutputTest {
    @Test
    public void testDebugOutputAlwaysVisible() {
        System.out.println("[DEBUG-TEST] System.out is visible");
        System.err.println("[DEBUG-TEST] System.err is visible");
        // Add more debug output here as needed
    }
}
