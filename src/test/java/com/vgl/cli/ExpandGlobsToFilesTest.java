package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpandGlobsToFilesTest {
    @Test
    void expandsDirectoriesButSkipsNestedRepos(@TempDir Path tmp) throws Exception {
        // Setup repo
        Git.init().setDirectory(tmp.toFile()).call();
        // create files
        Files.createDirectories(tmp.resolve("dirA/sub"));
            Files.writeString(tmp.resolve("dirA/a.txt"), "x");
            Files.writeString(tmp.resolve("dirA/sub/b.txt"), "y");
            // create nested repo
            Path nested = tmp.resolve("dirA/nested");
            Files.createDirectories(nested);
        Git.init().setDirectory(nested.toFile()).call();

        var repo = Git.open(tmp.toFile()).getRepository();
        List<String> result = Utils.expandGlobsToFiles(List.of("dirA"), tmp, repo);
        // Should include a.txt and sub/b.txt but not files inside dirA/nested
        assertThat(result).contains("dirA/a.txt", "dirA/sub/b.txt");
        assertThat(result).doesNotContain("dirA/nested");
    }
}
