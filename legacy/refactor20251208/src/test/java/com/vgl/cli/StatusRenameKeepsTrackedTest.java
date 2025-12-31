package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vgl.cli.test.utils.VglTestHarness;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class StatusRenameKeepsTrackedTest {
    // ...existing code...
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
			// Stage all changes at once to help JGit detect the rename
			git.add().addFilepattern(".").call();
			// Print JGit status after staging (only once, after add)
			org.eclipse.jgit.api.Status jgitStatus = git.status().call();
			System.out.println("[TEST-DEBUG] JGit status after staging:");
			System.out.println("  Added:    " + jgitStatus.getAdded());
			System.out.println("  Changed:  " + jgitStatus.getChanged());
			System.out.println("  Removed:  " + jgitStatus.getRemoved());
			System.out.println("  Missing:  " + jgitStatus.getMissing());
			System.out.println("  Modified: " + jgitStatus.getModified());
			System.out.println("  Untracked:" + jgitStatus.getUntracked());

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
			} finally {
				System.setProperty("user.dir", oldUserDir);
				System.setOut(oldOut);
				System.setErr(oldErr);
			}

			String out = baos.toString("UTF-8");

			// Always print the full captured CLI output (including debug lines)
			System.out.println("[TEST-DEBUG-FULL-OUTPUT]\n" + out);

			// renamed file should appear in Tracked section and not in Undecided
			assertThat(out).contains("-- Tracked Files:");
			assertThat(out).contains("aa.txt");
			assertThat(out).doesNotContain("-- Undecided Files:\n  aa.txt");
		}
	}
}

