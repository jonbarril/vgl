
package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

public class CommitCommandTest {
                @Test
                void commitAmendWithNewFlag(@TempDir Path tmp) throws Exception {
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("file.txt", "content");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "First");
                        repo.writeFile("file.txt", "modified");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "-new", "Amended");
                        assertThat(commitOutput.trim()).matches("[0-9a-f]{7}");
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
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("file.txt", "content");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "First");
                        repo.writeFile("file.txt", "modified");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "-add", "Added new changes");
                        assertThat(commitOutput.trim()).matches("[0-9a-f]{7}");
                        try (var git = repo.getGit()) {
                            var log = git.log().setMaxCount(1).call();
                            var commit = log.iterator().next();
                            assertThat(commit.getShortMessage()).isEqualTo("Added new changes");
                        }
                    }
                }

                @Test
                void commitWithoutMessage(@TempDir Path tmp) throws Exception {
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("file.txt", "content");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit");
                        assertThat(commitOutput).contains("Usage: vgl commit");
                    }
                }

                @Test
                void commitWithoutRepo(@TempDir Path tmp) throws Exception {
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createDir(tmp)) {
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "message");
                        String normalizedActual = commitOutput.replace("\r\n", "\n").trim();
                        assertThat(normalizedActual)
                            .withFailMessage("Expected missing repo message but got: %s", commitOutput)
                            .isEqualTo(com.vgl.cli.utils.MessageConstants.MSG_NO_REPO_RESOLVED);
                    }
                }

                @Test
                void commitWithConflictMarkers(@TempDir Path tmp) throws Exception {
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("conflict.txt", 
                            "line1\n" +
                            "<<<<<<< HEAD\n" +
                            "my change\n" +
                            "=======\n" +
                            "their change\n" +
                            ">>>>>>> branch\n" +
                            "line2\n");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "conflict.txt");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Should fail");
                        assertThat(commitOutput).contains("unresolved conflict markers");
                        assertThat(commitOutput).contains("conflict.txt");
                    }
                }

                @Test
                void commitMultipleStagedFiles(@TempDir Path tmp) throws Exception {
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("file1.txt", "content1");
                        repo.writeFile("file2.txt", "content2");
                        repo.writeFile("file3.txt", "content3");
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "file1.txt", "file2.txt", "file3.txt");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Add multiple files");
                        assertThat(commitOutput.trim()).matches("[0-9a-f]{7}");
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
                    try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                        com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                        repo.writeFile("untracked.txt", "content");
                        String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Auto-stage test");
                        assertThat(commitOutput.trim()).matches("[0-9a-f]{7}");
                        try (var git = repo.getGit()) {
                            var status = git.status().call();
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
            @Test
            void commitWithNothingToCommit(@TempDir Path tmp) throws Exception {
                try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                    com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                    repo.writeFile("file.txt", "content");
                    com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
                    com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
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
                    String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Empty");
                    assertThat(commitOutput)
                        .withFailMessage("Expected 'Nothing to commit' but got: %s\nGit status: %s", commitOutput, statusDetails)
                        .matches("(?s).*Nothing to commit.*");
                }
            }
        @Test
        void commitWithMessage(@TempDir Path tmp) throws Exception {
            try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
                com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
                repo.writeFile("file.txt", "content");
                com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
                String commitOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
                assertThat(commitOutput.trim()).matches("[0-9a-f]{7}");
                try (var git = repo.getGit()) {
                    var log = git.log().setMaxCount(1).call();
                    var commit = log.iterator().next();
                    assertThat(commit.getShortMessage()).isEqualTo("Initial commit");
                }
            }
        }
    @Test
    void minimalSanityTest(@TempDir Path tmp) throws Exception {
        try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(tmp)) {
            com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            assertThat(repo).isNotNull();
            assertThat(repo.getPath()).isNotNull();
            assertThat(java.nio.file.Files.exists(repo.getPath())).isTrue();
            String statusOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "status");
            assertThat(statusOutput).isNotNull();
        }
    }
}
