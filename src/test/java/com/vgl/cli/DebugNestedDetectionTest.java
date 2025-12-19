package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import com.vgl.cli.test.utils.VglTestHarness;
import com.vgl.cli.utils.Utils;
public class DebugNestedDetectionTest {
    @Test
    public void nestedDetection(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Use repo reference so static analysis doesn't flag it as unused
            System.out.println("repo initialized: " + repo);
            Path nested = tmp.resolve("nested");
            java.nio.file.Files.createDirectories(nested);
            System.out.println("tmp .git exists: " + java.nio.file.Files.exists(tmp.resolve(".git")) + " -> " + tmp.resolve(".git"));
            System.out.println("getGitRepoRoot(nested): " + Utils.getGitRepoRoot(nested));
            System.out.println("isNestedRepo(nested): " + Utils.isNestedRepo(nested));
            System.out.println("findGitRepo(nested): " + Utils.findGitRepo(nested, null));
        }
    }
}
