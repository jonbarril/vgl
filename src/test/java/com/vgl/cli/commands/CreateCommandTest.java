package com.vgl.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.vgl.cli.VglMain;
import com.vgl.cli.test.utils.StdIoCapture;
import com.vgl.cli.commands.helpers.Usage;
import com.vgl.cli.utils.FormatUtils;
import com.vgl.cli.utils.Messages;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void create_createsGitRepoVglFileAndGitignoreEntry() throws Exception {
        Path repoDir = tempDir.resolve("repo");

        try (StdIoCapture io = new StdIoCapture()) {
            int exit = new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"));
            assertThat(exit).isEqualTo(0);

            assertThat(io.stderr()).isEqualTo(Messages.warnTargetRepoNotCurrent(repoDir.toAbsolutePath().normalize()));
            assertThat(io.stdout()).isEqualTo(Messages.createdRepo(repoDir.toAbsolutePath().normalize(), "main"));
        }

        assertThat(Files.isDirectory(repoDir.resolve(".git"))).isTrue();
        assertThat(Files.isRegularFile(repoDir.resolve(".vgl"))).isTrue();
        assertThat(Files.isRegularFile(repoDir.resolve(".gitignore"))).isTrue();

        String ignore = Files.readString(repoDir.resolve(".gitignore"), StandardCharsets.UTF_8);
        assertThat(ignore).contains(".vgl");
    }

    @Test
    void create_failsIfRepoAlreadyExists() throws Exception {
        Path repoDir = tempDir.resolve("repo");

        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(0);
        }

        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-f"))).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Messages.repoAlreadyExists(repoDir.toAbsolutePath().normalize()));
        }
    }

    @Test
    void create_withPositionalTargetDir_printsUsage() throws Exception {
        Path repoDir = tempDir.resolve("repoPositional");

        try (StdIoCapture io = new StdIoCapture()) {
            int exit = VglMain.run(new String[] {"create", repoDir.toString()});
            assertThat(exit).isEqualTo(1);

            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.create());
        }

        assertThat(Files.exists(repoDir)).isFalse();
    }

    @Test
    void create_withLbInExistingRepo_createsAndSwitchesBranch() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed an initial commit so the branch can be created from HEAD.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit()
                .setAllowEmpty(true)
                .setMessage("init")
                .setAuthor(ident)
                .setCommitter(ident)
                .call();
        }

        String priorUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", repoDir.toString());
        try {
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"create", "-lb", "branch0"})).isEqualTo(0);
                assertThat(io.stderr()).isEmpty();

                String repoDisplay = FormatUtils.truncateMiddle(repoDir.toAbsolutePath().normalize().toString(), 35);
                int maxLen = Math.max(repoDisplay.length(), "(none)".length());

                String localLabelPad = FormatUtils.padRight("LOCAL:", 8);
                String remoteLabelPad = FormatUtils.padRight("REMOTE:", 8);

                String localLine = localLabelPad + FormatUtils.padRight(repoDisplay, maxLen) + " :: branch0";
                String remoteLine = remoteLabelPad + FormatUtils.padRight("(none)", maxLen) + " :: (none)";

                assertThat(io.stdout()).isEqualTo(Messages.switchedBranch("branch0") + "\n" + localLine + "\n" + remoteLine);
            }
        } finally {
            if (priorUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", priorUserDir);
            }
        }

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().findRef("refs/heads/branch0")).isNotNull();
            assertThat(git.getRepository().getBranch()).isEqualTo("branch0");
        }
    }

    @Test
    void create_withLrLbInNonCurrentRepo_warnsNoSwitchState() throws Exception {
        Path repoDir = tempDir.resolve("repoNonCurrent");
        try (StdIoCapture ignored = new StdIoCapture()) {
            assertThat(new CreateCommand().run(List.of("-lr", repoDir.toString(), "-lb", "main", "-f"))).isEqualTo(0);
        }

        // Seed an initial commit so the branch can be created from HEAD.
        try (Git git = Git.open(repoDir.toFile())) {
            PersonIdent ident = new PersonIdent("test", "test@example.com");
            git.commit().setAllowEmpty(true).setMessage("init").setAuthor(ident).setCommitter(ident).call();
        }

        // Run from a CWD that is NOT within the repo, but target the repo via -lr.
        String priorUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            try (StdIoCapture io = new StdIoCapture()) {
                assertThat(VglMain.run(new String[] {"create", "-lr", repoDir.toString(), "-lb", "branchX"})).isEqualTo(0);
                assertThat(io.stdout()).isEqualTo(Messages.switchedBranch("branchX"));
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
            assertThat(git.getRepository().findRef("refs/heads/branchX")).isNotNull();
            assertThat(git.getRepository().getBranch()).isEqualTo("branchX");
        }
    }

    @Test
    void create_withMissingLrValue_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"create", "-lr"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.create());
        }
    }

    @Test
    void create_withUnknownFlag_printsUsage() throws Exception {
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"create", "--nope"})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.create());
        }
    }

    @Test
    void create_withTooManyPositionals_printsUsage() throws Exception {
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");
        try (StdIoCapture io = new StdIoCapture()) {
            assertThat(VglMain.run(new String[] {"create", a.toString(), b.toString()})).isEqualTo(1);
            assertThat(io.stdout()).isEmpty();
            assertThat(io.stderr()).isEqualTo(Usage.create());
        }
    }
}
