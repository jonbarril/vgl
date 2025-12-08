package com.vgl.cli.status;

import com.vgl.cli.VglTestHarness;
import org.eclipse.jgit.api.Git;
import com.vgl.cli.commands.status.StatusFileCounts;
import org.eclipse.jgit.api.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusFileCountsTest {
    @Test
    void fromStatusCountsModifiedAddedRemoved(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // create and commit a tracked file
            repo.writeFile("a.txt", "one");
            repo.gitAdd("a.txt");
            repo.gitCommit("initial");

            // create and commit a file that will be deleted
            repo.writeFile("todelete.txt", "x");
            repo.gitAdd("todelete.txt");
            repo.gitCommit("adddelete");

            // create a staged (added) file
            repo.writeFile("added.txt", "new");
            try (Git g = repo.getGit()) {
                g.add().addFilepattern("added.txt").call();
            }

            // modify a tracked file (a.txt)
            repo.writeFile("a.txt", "one-two");

            // remove the committed file
            java.nio.file.Files.deleteIfExists(repo.getPath().resolve("todelete.txt"));

            try (Git g = repo.getGit()) {
                Status st = g.status().call();
                StatusFileCounts counts = StatusFileCounts.fromStatus(st);
                assertThat(counts.added).isEqualTo(1);
                assertThat(counts.modified).isEqualTo(1);
                assertThat(counts.removed).isEqualTo(1);
            }
        }
    }
}
