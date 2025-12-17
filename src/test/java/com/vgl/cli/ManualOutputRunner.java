package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Path;

/**
 * Manual output runner used to print CLI output for manual inspection.
 * This is intentionally assertion-free and uses the VglTestHarness to run
 * commands in an isolated temporary repository.
 */
public class ManualOutputRunner {
    @Test
    public void printStatus(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // Make an initial commit so -v/-vv show commit information
            repo.writeFile("README.md", "initial\n");
            repo.gitAdd("README.md");
            repo.gitCommit("Initial commit");

            System.out.println("=== status (default) ===");
            System.out.println(repo.runCommand("status"));

            System.out.println("=== status -v ===");
            System.out.println(repo.runCommand("status", "-v"));

            System.out.println("=== status -vv ===");
            System.out.println(repo.runCommand("status", "-vv"));
        }
    }

    @Test
    public void printHelp(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            System.out.println("=== help ===");
            System.out.println(repo.runCommand("help"));

            System.out.println("=== help -v ===");
            System.out.println(repo.runCommand("help", "-v"));

            System.out.println("=== help -vv ===");
            System.out.println(repo.runCommand("help", "-vv"));
        }
    }
}
