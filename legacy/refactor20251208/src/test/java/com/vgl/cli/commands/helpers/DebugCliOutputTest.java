package com.vgl.cli.commands.helpers;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebugCliOutputTest {
    @Test
    public void testVglMainDebugOutput() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", "build/install/vgl/lib/*", "com.vgl.cli.VglMain", "--debug-dummy"
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit = proc.waitFor();
        System.out.println("[DEBUG-CLI-TEST] Output:\n" + output);
        assertTrue(output.toString().contains("[DEBUG-CLI]"), "Expected debug output from CLI");
        assertTrue(exit == 0, "CLI should exit with code 0");
    }
}
