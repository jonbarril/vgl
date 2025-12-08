package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SwitchStatePersistenceTest {

    @Test
    public void persistedStateTakesPrecedenceOverCwd() throws Exception {
        Path dir1 = Files.createTempDirectory("vgl-test-repo1-");
        Path dir2 = Files.createTempDirectory("vgl-test-repo2-");
        // Init simple git repos
        Git g1 = Git.init().setDirectory(dir1.toFile()).call();
        g1.close();
        Git g2 = Git.init().setDirectory(dir2.toFile()).call();
        g2.close();

        // Use a temp state file so tests do not touch user.home
        Path stateFile = Files.createTempFile("vgl-state-", ".properties");
        System.setProperty("vgl.state", stateFile.toString());

        // Write persisted state pointing at dir1
        VglStateStore.VglState s = new VglStateStore.VglState();
        s.localDir = dir1.toAbsolutePath().toString();
        VglStateStore.write(s);

        // Simulate user wandering to dir2
        System.setProperty("user.dir", dir2.toAbsolutePath().toString());

        VglCli vgl = new VglCli();
        // resolveEffectiveRepoRoot should prefer persisted state (dir1) and not dir2
        java.nio.file.Path resolved = Utils.resolveEffectiveRepoRoot(vgl, java.nio.file.Paths.get(System.getProperty("user.dir")));
        assertEquals(dir1.toAbsolutePath().normalize(), resolved.toAbsolutePath().normalize());
    }
}
