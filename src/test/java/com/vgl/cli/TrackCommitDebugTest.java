package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.*;

public class TrackCommitDebugTest {

    @Test
    public void debugTrackAndCommit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            System.out.println("=== Step 1: Create repo ===");
            String createOutput = repo.runCommand("create", "-lr", tmp.toString());
            System.out.println(createOutput);
            System.out.println(".git exists: " + Files.exists(tmp.resolve(".git")));
            System.out.println(".vgl exists: " + Files.exists(tmp.resolve(".vgl")));
            
            System.out.println("\n=== Step 2: Write file ===");
            repo.writeFile("a.txt", "hello\n");
            System.out.println("a.txt exists: " + Files.exists(tmp.resolve("a.txt")));
            System.out.println("a.txt content: " + repo.readFile("a.txt"));
            
            System.out.println("\n=== Step 3: Track file ===");
            String trackOutput = repo.runCommand("track", "a.txt");
            System.out.println(trackOutput);
            
            System.out.println("\n=== Step 4: Check git status ===");
            try (var git = repo.getGit()) {
                var status = git.status().call();
                System.out.println("Added: " + status.getAdded());
                System.out.println("Changed: " + status.getChanged());
                System.out.println("Modified: " + status.getModified());
                System.out.println("Untracked: " + status.getUntracked());
            }
            
            System.out.println("\n=== Step 5: Commit ===");
            String commitOutput = repo.runCommand("commit", "initial");
            System.out.println(commitOutput);
        }
    }
}
