package com.vgl.cli.commands.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.test.utils.StdIoCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void computeDiffSummaryAndPrintsSummary() throws Exception {
        Path left = tempDir.resolve("left");
        Path right = tempDir.resolve("right");
        Files.createDirectories(left);
        Files.createDirectories(right);

        Files.writeString(left.resolve("a.txt"), "one\n");
        Files.writeString(right.resolve("a.txt"), "two\n");
        Files.writeString(right.resolve("b.txt"), "only\nline\n");

        Map<String, byte[]> lmap = DiffHelper.snapshotFiles(left, List.of("**"));
        Map<String, byte[]> rmap = DiffHelper.snapshotFiles(right, List.of("**"));

        DiffHelper.DiffSummary s = DiffHelper.computeDiffSummary(lmap, rmap);
        assertThat(s.perFileCounts).containsKeys("a.txt", "b.txt");
        assertThat(s.totalAdded).isGreaterThanOrEqualTo(1);

        try (StdIoCapture io = new StdIoCapture()) {
            java.util.Set<String> keys = new java.util.HashSet<>();
            keys.addAll(lmap.keySet());
            keys.addAll(rmap.keySet());
            DiffHelper.printSummary(System.out, s, keys.size());
            String out = io.stdout();
            assertThat(out).contains("Matched files:");
            assertThat(out).contains("Changed files:");
            assertThat(out).contains("a.txt");
            assertThat(out).contains("b.txt");
        }
    }
}
