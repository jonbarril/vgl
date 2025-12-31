package com.vgl.cli.utils;

import org.eclipse.jgit.lib.Repository;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.stream.Stream;

public final class Utils {
	private Utils() {}

	// Helper Methods
	private static Path currentDir() {
		return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
	}


	public static Repository openNearestGitRepo(File start) throws IOException {
		return openNearestGitRepo(start, null);
	}

	public static Repository openNearestGitRepo(File start, File ceiling) throws IOException {
		return com.vgl.cli.utils.GitUtils.openNearestGitRepo(start, ceiling);
	}

	public static String versionFromRuntime() {
		Package p = Utils.class.getPackage();
		String v = (p != null) ? p.getImplementationVersion() : null;
		if (v != null && !v.isBlank()) return v;
		String sys = System.getProperty("vgl.version");
		if (sys != null && !sys.isBlank()) return sys;
		return "MVP";
	}

	public static List<String> expandGlobs(List<String> globs) throws IOException {
		try {
			Path repoRoot = RepoUtils.getGitRepoRoot(currentDir());
			if (repoRoot != null) return expandGlobs(globs, repoRoot);
		} catch (Exception ignored) {}
		return expandGlobs(globs, null);
	}

	public static List<String> expandGlobs(List<String> globs, Path repoRoot) throws IOException {
		if (globs == null || globs.isEmpty()) return Collections.emptyList();
		if (repoRoot != null) {
			try {
				org.eclipse.jgit.api.Git git = RepoUtils.findGitRepo(repoRoot, null);
				if (git != null) {
					return RepoUtils.expandGlobsToFiles(globs, repoRoot.toAbsolutePath().normalize(), git.getRepository());
				}
			} catch (Exception ignored) {}
		}
		Path base = (repoRoot != null) ? repoRoot.toAbsolutePath().normalize() : currentDir();
		Set<String> out = new LinkedHashSet<>();
		for (String g : globs) {
			if ("*".equals(g) || ".".equals(g)) {
				try (Stream<Path> s = Files.walk(base)) {
					s.filter(p -> Files.isRegularFile(p)).forEach(p -> out.add(base.relativize(p).toString().replace('\\','/')));
				}
				continue;
			}
			final PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + g);
			try (Stream<Path> s = Files.walk(base)) {
				s.map(p -> base.relativize(p))
				 .filter(p -> m.matches(p))
				 .forEach(p -> out.add(p.toString().replace('\\','/')));
			}
		}
		return new ArrayList<>(out);
	}

	public static boolean isGitIgnored(Path path, Repository repository) {
		// Lightweight placeholder: callers should use JGit ignore checks when needed.
		return false;
	}

	public static java.util.Set<String> listNonIgnoredFiles(Path repoRoot, Repository repo) {
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		if (repo == null || repoRoot == null) return out;
		try {
			org.eclipse.jgit.treewalk.FileTreeIterator workingTreeIt = new org.eclipse.jgit.treewalk.FileTreeIterator(repo);
			org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repo);
			treeWalk.addTree(workingTreeIt);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				org.eclipse.jgit.treewalk.WorkingTreeIterator wti = (org.eclipse.jgit.treewalk.WorkingTreeIterator) treeWalk.getTree(0, org.eclipse.jgit.treewalk.WorkingTreeIterator.class);
				String pathStr = treeWalk.getPathString();
				if (wti == null) continue;
				try {
					if (!wti.isEntryIgnored()) {
						out.add(pathStr.replace('\\','/'));
					}
				} catch (Exception e) {
					// ignore and continue
				}
			}
			treeWalk.close();
		} catch (Exception e) {
			// fallback: nothing
		}
		return out;
	}


	public static boolean isInsideNestedRepo(Path repoRoot, Path path) {
		if (repoRoot == null || path == null) return false;
		try {
			Path cur = path.toAbsolutePath().normalize();
			Path root = repoRoot.toAbsolutePath().normalize();
			while (cur != null && !cur.equals(root)) {
				if (Files.exists(cur.resolve(".git"))) return true;
				cur = cur.getParent();
			}
		} catch (Exception e) {
			// ignore
		}
		return false;
	}

	public static java.util.Set<String> listNestedRepos(Path repoRoot) {
		return com.vgl.cli.utils.GitUtils.listNestedRepos(repoRoot);
	}

	public static String normalizeToRepoPath(Path repoRoot, Path file) {
		Path rel = repoRoot.relativize(file);
		String s = rel.toString().replace('\\', '/');
		return s;
	}
}
