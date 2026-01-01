package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.Messages;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwitchCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void switch_switchesBranch_andPrintsSwitchStateWhenCurrentRepo() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed an initial commit so checkout works in a non-unborn repo.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
            git.branchCreate().setName("branch0").call();
        }

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", repoDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"switch", "-lb", "branch0"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();

                String repoDisplay = FormatUtils.truncateMiddle(repoDir.toAbsolutePath().normalize().toString(), 35);
                int maxLen = Math.max(repoDisplay.length(), "(none)".length());

                String localLabelPad = FormatUtils.padRight("LOCAL:", 8);
                String remoteLabelPad = FormatUtils.padRight("REMOTE:", 8);

                String localLine = localLabelPad + FormatUtils.padRight(repoDisplay, maxLen) + " :: branch0";
                String remoteLine = remoteLabelPad + FormatUtils.padRight("(none)", maxLen) + " :: (none)";

                assertThat(io.stdout()).isEqualTo(Messages.switchedToExistingBranch("branch0") + "\n" + localLine + "\n" + remoteLine);
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("branch0");
        }
    }

    @Test
    void switch_withLrInNonCurrentRepo_warnsNoSwitchState() throws Exception {
        Path repoDir = tempDir.resolve("repoNonCurrent");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
            git.branchCreate().setName("branchX").call();
        }

        String priorUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"switch", "-lr", repoDir.toString(), "-lb", "branchX"})).isEqualTo(0);

                String repoDisplay = FormatUtils.truncateMiddle(repoDir.toAbsolutePath().normalize().toString(), 35);
                int maxLen = Math.max(repoDisplay.length(), "(none)".length());
                String localLabelPad = FormatUtils.padRight("LOCAL:", 8);
                String remoteLabelPad = FormatUtils.padRight("REMOTE:", 8);
                String localLine = localLabelPad + FormatUtils.padRight(repoDisplay, maxLen) + " :: branchX";
                String remoteLine = remoteLabelPad + FormatUtils.padRight("(none)", maxLen) + " :: (none)";

                assertThat(io.stdout()).isEqualTo(Messages.switchedToExistingBranch("branchX") + "\n" + localLine + "\n" + remoteLine);
                assertThat(io.stderr()).isEqualTo(Messages.warnTargetRepoNotCurrent(repoDir.toAbsolutePath().normalize()));
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("branchX");
        }
    }

    @Test
    void switch_missingBranch_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"switch"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isNotEmpty();
        }
    }
}
