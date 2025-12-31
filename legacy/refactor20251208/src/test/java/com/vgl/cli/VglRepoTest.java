
package com.vgl.cli;
import com.vgl.cli.utils.RepoUtils;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.services.VglRepo;
import com.vgl.cli.test.utils.TestProgress;

public class VglRepoTest {
            @Test
            void corruptedVglFileHandledGracefully(@TempDir Path tmp) throws Exception {
                printProgress("corruptedVglFileHandledGracefully");
                try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {}
                Path vglFile = tmp.resolve(".vgl");
                java.nio.file.Files.writeString(vglFile, "not=valid\n!!!corrupted!!!");
                try (VglRepo vgl = com.vgl.cli.utils.RepoUtils.findVglRepo(tmp)) {
                    // Should not throw, should use defaults or empty undecided
                    assertThat(vgl.getUndecidedFiles()).isEmpty();
                }
            }

            @Test
            void closeDoesNotThrowOnMultipleClose(@TempDir Path tmp) throws Exception {
                printProgress("closeDoesNotThrowOnMultipleClose");
                try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {}
                VglRepo vgl = com.vgl.cli.utils.RepoUtils.findVglRepo(tmp);
                vgl.close();
                // Should not throw if closed again
                vgl.close();
            }

            @Test
            void invalidRepoThrowsOrReturnsNull(@TempDir Path tmp) {
                printProgress("invalidRepoThrowsOrReturnsNull");
                // No .git directory
                VglRepo vgl = null;
                try {
                    vgl = com.vgl.cli.utils.RepoUtils.findVglRepo(tmp);
                    // Should be null or throw
                    assertThat(vgl).isNull();
                } catch (Exception e) {
                    // Acceptable: exception thrown for invalid repo
                    assertThat(e).isInstanceOf(Exception.class);
                } finally {
                    if (vgl != null) try { vgl.close(); } catch (Exception ignore) {}
                }
            }

            // Optional: Nested repo handling if supported by VglRepo
            // @Test
            // void nestedRepoIsIgnoredInUndecided(@TempDir Path tmp) throws Exception {
            //     printProgress("nestedRepoIsIgnoredInUndecided");
            //     try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {}
            //     Path nested = tmp.resolve("nested");
            //     java.nio.file.Files.createDirectories(nested);
            //     try (@SuppressWarnings("unused") Git git2 = Git.init().setDirectory(nested.toFile()).call()) {}
            //     try (VglRepo vgl = com.vgl.cli.utils.Utils.findVglRepo(tmp)) {
            //         java.util.List<String> undecided = java.util.Arrays.asList("nested/file.txt", "top.txt");
            //         vgl.setUndecidedFiles(undecided);
            //         vgl.saveConfig();
            //     }
            //     try (VglRepo vgl = com.vgl.cli.utils.Utils.findVglRepo(tmp)) {
            //         java.util.List<String> loaded = vgl.getUndecidedFiles();
            //         // Should not include files from nested repo if logic applies
            //         assertThat(loaded).contains("top.txt");
            //     }
            // }
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
        try (VglRepo vgl = RepoUtils.findVglRepo(tmp)) {
            assertThat(vgl).isNotNull();
            // Initially empty
            assertThat(vgl.getUndecidedFiles()).isEmpty();
            // Set undecided files
            List<String> undecided = Arrays.asList("foo.txt", "bar.txt", "sub/dir/file.java");
            vgl.setUndecidedFiles(undecided);
            vgl.saveConfig();
        }
        // Reload and verify
        try (VglRepo vgl2 = RepoUtils.findVglRepo(tmp)) {
            List<String> loaded = vgl2.getUndecidedFiles();
            assertThat(loaded).containsExactly("foo.txt", "bar.txt", "sub/dir/file.java");
        }
    }

    @Test
    void undecidedFilesCanBeCleared(@TempDir Path tmp) throws Exception {
            printProgress("undecidedFilesCanBeCleared");
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        try (VglRepo vgl = RepoUtils.findVglRepo(tmp)) {
            vgl.setUndecidedFiles(Arrays.asList("a.txt", "b.txt"));
            vgl.saveConfig();
        }
        // Clear undecided files
        try (VglRepo vgl = RepoUtils.findVglRepo(tmp)) {
            vgl.setUndecidedFiles(Collections.emptyList());
            vgl.saveConfig();
        }
        // Reload and verify
        try (VglRepo vgl = RepoUtils.findVglRepo(tmp)) {
            assertThat(vgl.getUndecidedFiles()).isEmpty();
        }
    }

    @Test
    void undecidedFilesHandlesNoVglFile(@TempDir Path tmp) throws Exception {
            printProgress("undecidedFilesHandlesNoVglFile");
        try (@SuppressWarnings("unused") Git git = Git.init().setDirectory(tmp.toFile()).call()) {
        }
        // No .vgl file present
        try (VglRepo vgl = RepoUtils.findVglRepo(tmp)) {
            assertThat(vgl.getUndecidedFiles()).isEmpty();
        }
    }
}
