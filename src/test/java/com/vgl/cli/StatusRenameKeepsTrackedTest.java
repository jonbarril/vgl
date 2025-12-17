package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusRenameKeepsTrackedTest {
	@Test
	public void renamedTrackedFileRemainsTracked(@TempDir Path td) throws Exception {
		Path repoDir = td.resolve("repo");
		Files.createDirectories(repoDir);

		// Use VglTestHarness helpers to create git repo and vgl config
		try (Git git = VglTestHarness.createGitRepo(repoDir)) {
			// create and commit a tracked file
			Files.writeString(repoDir.resolve("a.txt"), "hello");
			java.util.Properties props = new java.util.Properties();
			props.setProperty("local.dir", repoDir.toString().replace('\\', '/'));
			VglTestHarness.createVglConfig(repoDir, props);
			git.add().addFilepattern(".").call();
			git.commit().setMessage("initial").call();

			// rename a.txt -> aa.txt in working tree (no commit)
			Files.move(repoDir.resolve("a.txt"), repoDir.resolve("aa.txt"));

			// run status -vv
			java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			java.io.PrintStream oldOut = System.out;
			java.io.PrintStream oldErr = System.err;
			String oldUserDir = System.getProperty("user.dir");
			try {
				System.setProperty("user.dir", repoDir.toString());
				java.io.PrintStream ps = new java.io.PrintStream(baos, true, "UTF-8");
				System.setOut(ps);
				System.setErr(ps);
				new VglCli().run(new String[]{"status", "-vv"});
				String out = baos.toString("UTF-8");

				// renamed file should appear in Tracked section and not in Undecided
				assertThat(out).contains("-- Tracked Files:");
				assertThat(out).contains("aa.txt");
				assertThat(out).doesNotContain("-- Undecided Files:\n  aa.txt");
			} finally {
				System.setProperty("user.dir", oldUserDir);
				System.setOut(oldOut);
				System.setErr(oldErr);
			}
		}
	}
}

