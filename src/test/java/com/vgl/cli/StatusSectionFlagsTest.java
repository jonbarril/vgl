package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vgl.cli.test.utils.VglTestHarness;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;

public class StatusSectionFlagsTest {
    @Test
    void statusLocalSectionOnly(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-local");
            assertThat(output).contains("LOCAL");
            assertThat(output).doesNotContain("REMOTE");
            assertThat(output).doesNotContain("COMMITS");
            assertThat(output).doesNotContain("FILES");
        }
    }

    @Test
    void statusRemoteSectionOnly(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-remote");
            assertThat(output).contains("REMOTE");
            assertThat(output).doesNotContain("LOCAL");
            assertThat(output).doesNotContain("COMMITS");
            assertThat(output).doesNotContain("FILES");
        }
    }

    @Test
    void statusCommitsSectionOnly(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-commits");
            assertThat(output).contains("COMMITS");
            assertThat(output).doesNotContain("LOCAL");
            assertThat(output).doesNotContain("REMOTE");
            assertThat(output).doesNotContain("FILES");
        }
    }

    @Test
    void statusFilesSectionOnly(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            VglTestHarness.runVglCommand(repo.getPath(), "create", "-lr", tmp.toString());
            String output = VglTestHarness.runVglCommand(repo.getPath(), "status", "-files");
            assertThat(output).contains("FILES");
            assertThat(output).doesNotContain("LOCAL");
            assertThat(output).doesNotContain("REMOTE");
            assertThat(output).doesNotContain("COMMITS");
        }
    }
}
