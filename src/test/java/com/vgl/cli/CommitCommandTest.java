package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

public class CommitCommandTest {

    @Test
    void commitWithMessage(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            
            String output = repo.runCommand("commit", "Initial commit");
            
            assertThat(output.trim()).matches("[0-9a-f]{7}");
            
            // Verify commit was created
            try (var git = repo.getGit()) {
                var log = git.log().setMaxCount(1).call();
                var commit = log.iterator().next();
                assertThat(commit.getShortMessage()).isEqualTo("Initial commit");
            }
        }
    }

    @Test
    void commitWithNothingToCommit(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            // Make an initial commit
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            // Print git status before second commit for debugging
            String statusDetails;
            try (var git = repo.getGit()) {
                var status = git.status().call();
                statusDetails = "Added=" + status.getAdded()
                    + ", Changed=" + status.getChanged()
                    + ", Removed=" + status.getRemoved()
                    + ", Modified=" + status.getModified()
                    + ", Missing=" + status.getMissing()
                    + ", Untracked=" + status.getUntracked();
            }
            // Now try to commit again with no changes
            String output = repo.runCommand("commit", "Empty");
            assertThat(output)
                .withFailMessage("Expected 'Nothing to commit' but got: %s\nGit status: %s", output, statusDetails)
                .matches("(?s).*Nothing to commit.*");
        }
    }

    @Test
    void commitAmendWithNewFlag(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            // First commit
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "First");
            
            // Amend
            repo.writeFile("file.txt", "modified");
            String output = repo.runCommand("commit", "-new", "Amended");
            
            assertThat(output.trim()).matches("[0-9a-f]{7}");
            
            // Verify only one commit exists with amended message
            try (var git = repo.getGit()) {
                var log = git.log().setMaxCount(2).call();
                var commits = new java.util.ArrayList<org.eclipse.jgit.revwalk.RevCommit>();
                log.forEach(commits::add);
                assertThat(commits).hasSize(1);
                assertThat(commits.get(0).getShortMessage()).isEqualTo("Amended");
            }
        }
    }

    @Test
    void commitAmendWithAddFlag(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            // First commit
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "First");
            
            // Amend with -add
            repo.writeFile("file.txt", "modified");
            String output = repo.runCommand("commit", "-add", "Added new changes");
            
            assertThat(output.trim()).matches("[0-9a-f]{7}");
            
            try (var git = repo.getGit()) {
                var log = git.log().setMaxCount(1).call();
                var commit = log.iterator().next();
                assertThat(commit.getShortMessage()).isEqualTo("Added new changes");
            }
        }
    }

    @Test
    void commitWithoutMessage(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("file.txt", "content");
            
            String output = repo.runCommand("commit");
            
            assertThat(output).contains("Usage: vgl commit");
        }
    }

    @Test
    void commitWithoutRepo(@TempDir Path tmp) throws Exception {
        // Note: This test is difficult to properly isolate because JGit's openGit()
        // searches upward for .git, and when running tests from within the vgl workspace,
        // it will find the workspace's .git directory.
        // 
        // The real-world scenario where this matters is when a user runs vgl commit
        // from a directory that's not inside any git repo at all.
        // 
        // For now, we just verify that attempting to commit without proper setup
        // doesn't crash and provides some error output.
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createDir(tmp)) {
            String output = repo.runCommand("commit", "message");
            
            // Should contain some error indication (exact message may vary depending
            // on whether parent workspace .git is found or not)
            assertThat(output).matches("(?s).*(No local repository|Missing unknown|Error).*");
        }
    }

    @Test
    void commitWithConflictMarkers(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            // Create file with conflict markers
            repo.writeFile("conflict.txt", 
                "line1\n" +
                "<<<<<<< HEAD\n" +
                "my change\n" +
                "=======\n" +
                "their change\n" +
                ">>>>>>> branch\n" +
                "line2\n");
            repo.runCommand("track", "conflict.txt");
            
            String output = repo.runCommand("commit", "Should fail");
            
            assertThat(output).contains("unresolved conflict markers");
            assertThat(output).contains("conflict.txt");
        }
    }

    @Test
    void commitMultipleStagedFiles(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            repo.writeFile("file1.txt", "content1");
            repo.writeFile("file2.txt", "content2");
            repo.writeFile("file3.txt", "content3");
            repo.runCommand("track", "file1.txt", "file2.txt", "file3.txt");
            
            String output = repo.runCommand("commit", "Add multiple files");
            
            assertThat(output.trim()).matches("[0-9a-f]{7}");
            
            // Verify all files are in the commit
            try (var git = repo.getGit()) {
                var log = git.log().setMaxCount(1).call();
                var commit = log.iterator().next();
                var tree = commit.getTree();
                try (var treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository())) {
                    treeWalk.addTree(tree);
                    var files = new java.util.HashSet<String>();
                    while (treeWalk.next()) {
                        files.add(treeWalk.getPathString());
                    }
                    assertThat(files).contains("file1.txt", "file2.txt", "file3.txt");
                }
            }
        }
    }

    @Test
    void commitStagesUntrackedFilesAutomatically(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            
            // Create file but don't track it explicitly
            repo.writeFile("untracked.txt", "content");
            
            String output = repo.runCommand("commit", "Auto-stage test");
            
            assertThat(output.trim()).matches("[0-9a-f]{7}");
            
            // Verify the untracked file was committed
            try (var git = repo.getGit()) {
                var status = git.status().call();
                // Ignore .vgl in untracked files
                assertThat(status.getUntracked().stream().filter(f -> !f.equals(".vgl")).toList()).isEmpty();

                var log = git.log().setMaxCount(1).call();
                var commit = log.iterator().next();
                var tree = commit.getTree();
                try (var treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository())) {
                    treeWalk.addTree(tree);
                    var files = new java.util.HashSet<String>();
                    while (treeWalk.next()) {
                        files.add(treeWalk.getPathString());
                    }
                    assertThat(files).contains("untracked.txt");
                }
            }
        }
    }
}
