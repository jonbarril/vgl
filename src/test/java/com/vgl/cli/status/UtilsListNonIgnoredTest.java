package com.vgl.cli.status;

import com.vgl.cli.VglTestHarness;
import com.vgl.cli.Utils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsListNonIgnoredTest {
    @Test
    void listNonIgnoredExcludesIgnoredAndNested(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            // create a normal file
            repo.writeFile("keep.txt", "keep");
            // create an ignored file and add to .gitignore
            repo.writeFile("ignored.tmp", "nope");
            repo.writeFile(".gitignore", "ignored.tmp\n");
            // create a nested repo
            Path nested = repo.getPath().resolve("nested");
            java.nio.file.Files.createDirectories(nested);
            Git.init().setDirectory(nested.toFile()).call();

            try (Git g = repo.getGit()) {
                Set<String> files = Utils.listNonIgnoredFiles(repo.getPath(), g.getRepository());
                assertThat(files).contains("keep.txt");
                assertThat(files).doesNotContain("ignored.tmp");
                assertThat(files).doesNotContain("nested/n.txt");
            }
        }
    }
}
