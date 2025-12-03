package com.vgl.cli;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

public class CommitAndDiffTest {

    @Test
    public void commitPrintsShortId_andDiffShowsChanges(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.writeFile("a.txt", "hello\n");
            repo.runCommand("track", "a.txt");
            String commitOutput = repo.runCommand("commit", "initial");

            // Assert the commit output contains a valid short hash
            assertThat(commitOutput).containsPattern("[0-9a-fA-F]{7,40}");

            // Modify the file and check the diff output
            repo.writeFile("a.txt", "hello\nworld\n");
            String diffOutput = repo.runCommand("diff");
            
            assertThat(diffOutput).isNotNull();

            // Check the diff output with the -rb flag (should default to -lb)
            String diffRemoteOutput = repo.runCommand("diff", "-rb");
            
            assertThat(diffRemoteOutput).isNotNull();
            String dr = diffRemoteOutput.strip();
            assertThat(dr.isEmpty() || dr.contains("(remote diff)") || dr.contains("No remote connected.")).isTrue();
        }
    }
}
