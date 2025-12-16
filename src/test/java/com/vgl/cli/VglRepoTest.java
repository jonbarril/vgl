package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.vgl.cli.utils.Utils;

public class VglRepoTest {
        private static void printProgress(String testName) {
            TestProgress.print(VglRepoTest.class, testName);
        }
    @Test
    void undecidedFilesReadWriteRoundTrip(@TempDir Path tmp) throws Exception {
            printProgress("undecidedFilesReadWriteRoundTrip");
        // Create git repo
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        // Create VglRepo
        try (VglRepo vgl = Utils.findVglRepo(tmp)) {
            assertThat(vgl).isNotNull();
            // Initially empty
            assertThat(vgl.getUndecidedFiles()).isEmpty();
            // Set undecided files
            List<String> undecided = Arrays.asList("foo.txt", "bar.txt", "sub/dir/file.java");
            vgl.setUndecidedFiles(undecided);
            vgl.saveConfig();
        }
        // Reload and verify
        try (VglRepo vgl2 = Utils.findVglRepo(tmp)) {
            List<String> loaded = vgl2.getUndecidedFiles();
            assertThat(loaded).containsExactly("foo.txt", "bar.txt", "sub/dir/file.java");
        }
    }

    @Test
    void undecidedFilesCanBeCleared(@TempDir Path tmp) throws Exception {
            printProgress("undecidedFilesCanBeCleared");
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        try (VglRepo vgl = Utils.findVglRepo(tmp)) {
            vgl.setUndecidedFiles(Arrays.asList("a.txt", "b.txt"));
            vgl.saveConfig();
        }
        // Clear undecided files
        try (VglRepo vgl = Utils.findVglRepo(tmp)) {
            vgl.setUndecidedFiles(Collections.emptyList());
            vgl.saveConfig();
        }
        // Reload and verify
        try (VglRepo vgl = Utils.findVglRepo(tmp)) {
            assertThat(vgl.getUndecidedFiles()).isEmpty();
        }
    }

    @Test
    void undecidedFilesHandlesNoVglFile(@TempDir Path tmp) throws Exception {
            printProgress("undecidedFilesHandlesNoVglFile");
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        // No .vgl file present
        try (VglRepo vgl = Utils.findVglRepo(tmp)) {
            assertThat(vgl.getUndecidedFiles()).isEmpty();
        }
    }
}
