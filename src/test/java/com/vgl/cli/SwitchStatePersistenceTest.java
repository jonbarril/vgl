package com.vgl.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SwitchStatePersistenceTest {

    @Test
    public void persistedStateTakesPrecedenceOverCwd() throws Exception {
        // Use VglTestHarness to create two repos in build/tmp (never user home)
        Path testBase = Path.of("build", "tmp", "switch-state-test").toAbsolutePath();
        Files.createDirectories(testBase);
        Path dir1 = testBase.resolve("repo1");
        Path dir2 = testBase.resolve("repo2");
        if (Files.exists(dir1)) deleteRecursively(dir1);
        if (Files.exists(dir2)) deleteRecursively(dir2);
        try (VglTestHarness.VglTestRepo repo1 = VglTestHarness.createRepo(dir1)) {
            try (VglTestHarness.VglTestRepo repo2 = VglTestHarness.createRepo(dir2)) {
                // Use a temp state file so tests do not touch user.home
                Path stateFile = Files.createTempFile("vgl-state-", ".properties");
                System.setProperty("vgl.state", stateFile.toString());

                // Write persisted state pointing at repo1
                VglCli vgl = new VglCli();
                vgl.setLocalDir(repo1.getPath().toString());
                vgl.save();

                // Simulate user wandering to repo2
                System.setProperty("user.dir", repo2.getPath().toString());

                // resolveEffectiveRepoRoot should prefer persisted state (repo1) and not repo2
                java.nio.file.Path resolved = Utils.resolveEffectiveRepoRoot(vgl, java.nio.file.Paths.get(System.getProperty("user.dir")));
                assertEquals(repo1.getPath().toAbsolutePath().normalize(), resolved.toAbsolutePath().normalize());
            }
        }
    }

    private static void deleteRecursively(Path path) throws java.io.IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (java.io.IOException e) { }
                });
        }
    }
}
