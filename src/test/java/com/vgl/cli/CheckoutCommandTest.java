package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class CheckoutCommandTest {
    @Test
    void checkoutBranchSwitchesBranch(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            repo.runCommand("track", "file.txt");
            repo.runCommand("commit", "Initial commit");
            repo.runCommand("split", "-into", "-lb", "feature");
            String output = repo.runCommand("checkout", "-lb", "feature");
            assertThat(output).contains("Checked out branch 'feature'");
        }
    }

    @Test
    void checkoutNonexistentBranchShowsError(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("checkout", "-lb", "nope");
            assertThat(output).contains("does not exist");
        }
    }
}
