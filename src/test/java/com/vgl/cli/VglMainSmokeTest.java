package com.vgl.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VglMainSmokeTest {

    @Test
    void frameworkIsWorking() {
        // SIMPLE always-pass test so VS Code can discover the test framework
        assertTrue(true);
    }

    @Test
    void mainRunsWithoutThrowing() {
        // Basic smoke test: main() with no args should not crash
        assertDoesNotThrow(() -> VglMain.main(new String[0]));
    }
}