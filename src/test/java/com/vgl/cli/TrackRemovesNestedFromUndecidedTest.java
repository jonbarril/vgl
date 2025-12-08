package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TrackRemovesNestedFromUndecidedTest {

    @Test
    public void trackAllRemovesNestedReposFromUndecided(@TempDir Path td) throws Exception {
        Path repo = td.resolve("repo");
        Files.createDirectories(repo);
        try (VglTestHarness.VglTestRepo r = VglTestHarness.createRepo(repo)) {
            // Create a regular file that should be undecided
            r.writeFile("subdir/q.txt", "data");
            // Create two nested repositories
            Path repo1 = repo.resolve("Repo1");
            Path repo2 = repo.resolve("Repo2");
            Files.createDirectories(repo1);
            Files.createDirectories(repo2);
            Git.init().setDirectory(repo1.toFile()).call();
            Git.init().setDirectory(repo2.toFile()).call();

            // Create a .vgl file with undecided entries including the nested repo names
            String cfg = "undecided.files=subdir/q.txt,Repo2,Repo1\n" +
                         "local.dir=" + repo.toAbsolutePath().toString().replace('\\','/') + "\n";
            Files.writeString(repo.resolve(".vgl"), cfg);

            String out = r.runCommand("track", "-all");

            // Ensure no debug markers
            assertThat(out).doesNotContain("[vgl.debug");

            // Read .vgl after running track -all and ensure nested entries are gone from undecided
            String after = Files.readString(repo.resolve(".vgl"));
            assertThat(after).doesNotContain("Repo1").doesNotContain("Repo2");
        }
    }
}
