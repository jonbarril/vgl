    private static void deleteRecursively(java.nio.file.Path path) throws java.io.IOException {
        if (java.nio.file.Files.exists(path)) {
            java.nio.file.Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); } catch (java.io.IOException e) { }
                });
        }
    }

package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class StatusRenameUnionTest {

    private static void deleteRecursively(java.nio.file.Path path) throws java.io.IOException {
        if (java.nio.file.Files.exists(path)) {
            java.nio.file.Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); } catch (java.io.IOException e) { }
                });
        }
    }

    @Test
    public void commitAndWorkingRenamesAreUnioned() throws Exception {
        Path testBase = Path.of("build", "tmp", "status-rename-union-test").toAbsolutePath();
        Files.createDirectories(testBase);
        Path repo = testBase.resolve("repo");
        if (Files.exists(repo)) deleteRecursively(repo);
        Git git = Git.init().setDirectory(repo.toFile()).call();

        // Create initial file and commit
        Path a = repo.resolve("a.txt");
        Files.writeString(a, "hello");
        git.add().addFilepattern("a.txt").call();
        git.commit().setMessage("add a").call();

        // Rename a -> b and commit (this should be detected as a commit-derived rename)
        Path b = repo.resolve("b.txt");
        Files.move(a, b);
        git.add().addFilepattern("b.txt").call();
        git.rm().addFilepattern("a.txt").call();
        git.commit().setMessage("rename a->b").call();

        // Now perform a working-tree rename b -> c (uncommitted)
        Path c = repo.resolve("c.txt");
        Files.move(b, c);
        // no git add/commit for c

        // Compute sets
        org.eclipse.jgit.api.Status status = git.status().call();
        java.util.Set<String> commitRenames = com.vgl.cli.commands.status.StatusSyncFiles.computeCommitRenamedSet(git, status, "", "main");
        java.util.Map<String, String> workingRenames = com.vgl.cli.commands.status.StatusSyncFiles.computeWorkingRenames(git);

        // commitRenames should include b.txt (the committed rename target)
        assertTrue(commitRenames.contains("b.txt"), "commitRenames should include b.txt");

        // workingRenames should map b.txt -> c.txt
        assertTrue(workingRenames.containsValue("c.txt") || workingRenames.containsKey("b.txt"), "workingRenames should include mapping to c.txt");

        // Union should include both b.txt and c.txt
        java.util.Set<String> union = new java.util.LinkedHashSet<>();
        union.addAll(commitRenames);
        union.addAll(workingRenames.values());
        assertTrue(union.contains("b.txt") && union.contains("c.txt"), "Union must contain both committed and working rename targets");

        git.close();
    }
}
