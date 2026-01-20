package com.vgl.cli.commands.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class DiffHelperNewlineTest {

    @Test
    void normalizeNewlines_ignoresCrLfVsLf() {
        Map<String, byte[]> a = new HashMap<>();
        Map<String, byte[]> b = new HashMap<>();

        a.put("file.txt", "line1\nline2\n".getBytes(StandardCharsets.UTF_8));
        b.put("file.txt", "line1\r\nline2\r\n".getBytes(StandardCharsets.UTF_8));

        DiffHelper.DiffSummary s = DiffHelper.computeDiffSummary(a, b);
        assertThat(s.perFileCounts).isEmpty();
    }
}
