package com.vgl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.test.utils.StdIoCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VglMainSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void vglMain_canCreateAndDeleteRepo() {
        Path repoDir = tempDir.resolve("repo");

        try (StdIoCapture ignored = new StdIoCapture()) {
            int createExit = VglMain.run(new String[] {"create", "-lr", repoDir.toString(), "-f"});
            assertThat(createExit).isEqualTo(0);
            assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
            assertThat(Files.isRegularFile(repoDir.resolve(".vgl"))).isTrue();

            int deleteExit = VglMain.run(new String[] {"delete", "-lr", repoDir.toString(), "-f"});
            assertThat(deleteExit).isEqualTo(0);
            assertThat(Files.exists(repoDir.resolve(".git"))).isFalse();
            assertThat(Files.exists(repoDir.resolve(".vgl"))).isFalse();
        }
    }
}
