package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class CheckinCommandTest {
    @Test
    void checkinStagesAndCommits(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            String output = repo.runCommand("checkin", "file.txt", "-m", "msg");
            assertThat(output).contains("Committed");
        }
    }

    @Test
    void checkinShowsErrorOnNoFiles(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            repo.runCommand("create", "-lr", tmp.toString());
            String output = repo.runCommand("checkin");
            assertThat(output).contains("No files specified");
        }
    }
}
