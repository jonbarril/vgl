package com.vgl.cli;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
public class SmokeTest {
    @Test
    void runtimeVersionAvailable() {
        String v = Utils.versionFromRuntime();
        assertThat(v).isNotNull().isNotBlank();
    }
}
