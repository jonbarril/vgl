
package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class CheckinCommandTest {
    @Test
    void checkinStagesAndCommits(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            repo.writeFile("file.txt", "content");
            String output = VglTestHarness.runVglCommand(repo.getPath(), "checkin", "file.txt", "-m", "msg");
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput)
                .withFailMessage("Expected commit confirmation but got: %s", output)
                .contains(com.vgl.cli.utils.MessageConstants.MSG_COMMIT_SUCCESS);
        }
    }

    @Test
    void checkinShowsErrorOnNoFiles(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "checkin");
            String normalizedOutput = output.replace("\r\n", "\n").trim();
            assertThat(normalizedOutput)
                .withFailMessage("Expected 'No files specified' error but got: %s", output)
                .contains(com.vgl.cli.utils.MessageConstants.MSG_NO_FILES_SPECIFIED);
        }
    }
}
