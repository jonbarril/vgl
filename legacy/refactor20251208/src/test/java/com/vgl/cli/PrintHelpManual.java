package com.vgl.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.vgl.cli.test.utils.VglTestHarness;

public class PrintHelpManual {
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("vgl-manual-help");
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
