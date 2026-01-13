package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.RepoTestUtils;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.test.utils.UserDirOverride;
import com.vgl.cli.utils.Messages;
import com.vgl.cli.utils.VglConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckoutCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void checkout_clonesRemoteAndWritesVglConfig() throws Exception {
        Path remoteDir = tempDir.resolve("remote.git");
        RepoTestUtils.initBareRemoteWithSeedCommit(tempDir, remoteDir, "main");

        Path targetDir = tempDir.resolve("target");
        String remoteUrl = remoteDir.toUri().toString();

        try (UserDirOverride ignored = new UserDirOverride(targetDir);
            StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"checkout", "-f", "-rr", remoteUrl, "-rb", "main"}))
                .isEqualTo(0);
            assertThat(io.stderr()).isEmpty();
            assertThat(io.stdout()).contains(Messages.checkoutCompleted(targetDir.toAbsolutePath().normalize(), "main"));
            assertThat(io.stdout()).contains("CONTEXT:");
        }

        assertThat(Files.exists(targetDir.resolve(".git"))).isTrue();
        assertThat(Files.exists(targetDir.resolve(VglConfig.FILENAME))).isTrue();

        Properties props = RepoTestUtils.readVglProps(targetDir);
        assertThat(props.getProperty(VglConfig.KEY_REMOTE_URL)).isEqualTo(remoteUrl);
        assertThat(props.getProperty(VglConfig.KEY_REMOTE_BRANCH)).isEqualTo("main");
        assertThat(props.getProperty(VglConfig.KEY_LOCAL_BRANCH)).isEqualTo("main");
    }
}
