package com.vgl.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class DebugCommitFlowTest {

    @Test
    public void reproduceCommitFlow(@TempDir Path tmp) throws Exception {
        try (VglTestHarness.VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
            String out;
            out = repo.runCommand("create", "-lr", tmp.toString());
            System.out.println("CREATE OUTPUT:\n" + out);

            repo.writeFile("test.txt", "line1\nline2\n");
            out = repo.runCommand("track", "test.txt");
            System.out.println("TRACK OUTPUT:\n" + out);

            out = repo.runCommand("commit", "initial");
            System.out.println("COMMIT OUTPUT:\n" + out);

            repo.writeFile("test.txt", "line1\nline2\nline3\n");
            out = repo.runCommand("diff");
            System.out.println("DIFF OUTPUT:\n" + out);
        }
    }
}
