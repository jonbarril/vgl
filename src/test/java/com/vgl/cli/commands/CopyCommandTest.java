package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CopyCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void copy_fromRemote_clonesAndCreatesLocalOnlyVglConfig() throws Exception {
        Path remoteDir = tempDir.resolve("remote.git");
        RepoTestUtils.initBareRemoteWithSeedCommit(tempDir, remoteDir, "main");

        Path targetDir = tempDir.resolve("target");
        String remoteUrl = remoteDir.toUri().toString();

        try (UserDirOverride ignored = new UserDirOverride(targetDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"copy", "-from", "-rr", remoteUrl, "-rb", "main"}))
                .isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains("Copied repository.");
        }

        assertThat(Files.exists(targetDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(targetDir.resolve(VglConfig.FILENAME))).isTrue();

        Properties props = RepoTestUtils.readVglProps(targetDir);
        assertThat(props.getProperty(VglConfig.KEY_REMOTE_URL)).isEqualTo("");
        assertThat(props.getProperty(VglConfig.KEY_REMOTE_BRANCH)).isEqualTo("");
        assertThat(props.getProperty(VglConfig.KEY_LOCAL_BRANCH)).isEqualTo("main");
    }
}
