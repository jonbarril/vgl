package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import static org.assertj.core.api.Assertions.*;
import com.vgl.cli.test.utils.VglTestHarness;
import java.nio.file.Path;

import java.nio.file.Files;

public class CheckoutCommandTest {
    @Test
    void checkoutBranchSwitchesBranch(@TempDir Path tmp) throws Exception {
        Path sourceRepoDir = tmp.resolve("source");
        Path targetDir = tmp.resolve("target");
        java.nio.file.Files.createDirectories(sourceRepoDir);
        // Do not pre-create targetDir; let checkout create it
        try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(sourceRepoDir)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", sourceRepoDir.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            VglTestHarness.runVglCommand(repo.getPath(), "split", "-into", "-lb", "feature");
            // Push the new branch to the remote (itself, since we use local path as remote)
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(sourceRepoDir.toFile())) {
                git.push().setRemote(sourceRepoDir.toUri().toString()).add("feature").call();
            }
            // Simulate remote by using local repo path as URL
            Path parentDir = targetDir.getParent();
            // Run checkout from parent, but all post-checkout commands from targetDir
            String output = VglTestHarness.runVglCommand(parentDir, "checkout", "-rr", sourceRepoDir.toUri().toString(), "-rb", "feature", targetDir.getFileName().toString());
            // ...
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput)
                .withFailMessage("Expected branch checkout confirmation but got: %s", output)
                .contains(com.vgl.cli.utils.MessageConstants.MSG_CHECKOUT_SUCCESS_PREFIX + "feature'");
            // Run status in the checked-out directory to debug state visibility
            // String statusOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(targetDir, "status");
            // ...
        }
    }

    @Test
    void checkoutNonexistentBranchShowsError(@TempDir Path tmp) throws Exception {
        Path sourceRepoDir = tmp.resolve("source");
        Path targetDir = tmp.resolve("target");
        java.nio.file.Files.createDirectories(sourceRepoDir);
        // Do not pre-create targetDir; let checkout create it
        try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(sourceRepoDir)) {
            com.vgl.cli.test.utils.VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", sourceRepoDir.toString());
            // Simulate remote by using local repo path as URL
            Path parentDir = targetDir.getParent();
            // Run checkout from parent, but all post-checkout commands from targetDir
            String output = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(parentDir, "checkout", "-rr", sourceRepoDir.toUri().toString(), "-rb", "nope", targetDir.getFileName().toString());
            // ...
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            // If checkout failed, check error in the checked-out directory
            if (!Files.exists(targetDir.resolve(".git"))) {
                // The checkout did not create the target repo, so error is expected in parent
                assertThat(normalizedOutput)
                    .withFailMessage("Expected 'does not exist' error but got: %s", output)
                    .contains(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST);
            } else {
                // If the repo was created, check error in the target directory
                String statusOutput = com.vgl.cli.test.utils.VglTestHarness.runVglCommand(targetDir, "status");
                // ...
                assertThat(statusOutput)
                    .withFailMessage("Expected 'does not exist' error in status but got: %s", statusOutput)
                    .contains(com.vgl.cli.utils.MessageConstants.MSG_BRANCH_DOES_NOT_EXIST);
            }
        }
    }

    @Test
    void checkoutDefaultBranchFromLocalRepo(@TempDir Path tmp) throws Exception {
        Path sourceRepoDir = tmp.resolve("source");
        Path targetDir = tmp.resolve("target");
        java.nio.file.Files.createDirectories(sourceRepoDir);
        // Create a repo with default branch 'main'
        try (com.vgl.cli.test.utils.VglTestHarness.VglTestRepo repo = com.vgl.cli.test.utils.VglTestHarness.createRepo(sourceRepoDir)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", sourceRepoDir.toString());
            repo.writeFile("file.txt", "content");
            VglTestHarness.runVglCommand(repo.getPath(), "track", "file.txt");
            VglTestHarness.runVglCommand(repo.getPath(), "commit", "Initial commit");
            // Simulate remote by using local repo path as URL
            Path parentDir = targetDir.getParent();
            // Run checkout from parent, but all post-checkout commands from targetDir
            String output = VglTestHarness.runVglCommand(parentDir, "checkout", "-rr", sourceRepoDir.toUri().toString(), targetDir.getFileName().toString());
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput)
                .withFailMessage("Expected checkout confirmation for default branch but got: %s", output)
                .contains(com.vgl.cli.utils.MessageConstants.MSG_CHECKOUT_SUCCESS_PREFIX + "main'");
        }
    }
}
