


package com.vgl.cli.utils;
import com.vgl.cli.VglRepo;
import com.vgl.cli.RepoResolution;
import com.vgl.cli.VglStateStore;
import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized repo/state resolution helper used by CLI commands.
 * Returns a rich {@link RepoResolution} describing the outcome. Backwards-compatible
 * convenience methods are provided for callers that only need a raw Git or VglRepo.
 */
public final class RepoResolver {
	private RepoResolver() {}

	/**
	 * Resolve repository state starting from the current working directory.
	 */
	public static RepoResolution resolveForCommand() throws IOException {
		Path cwd = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
		return resolveForCommand(cwd);
	}

	/**
	 * Resolve repository state starting from the supplied path. This may prompt (interactive)
	 * and may create a `.vgl` when appropriate (matching existing behavior in `Utils`).
	 */
	public static RepoResolution resolveForCommand(Path start) throws IOException {

		if (start == null) start = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
		// ...existing repo state logic...


		Path repoRoot = null;

		// Fast path: if the user-level VGL state points at a repository, validate
		// it first. This lets us fail fast when persisted state is corrupted
		// (refs a non-existent path) instead of scanning large trees.
		try {
			VglStateStore.VglState s = VglStateStore.read();
			if (s != null && s.localDir != null && !s.localDir.isBlank()) {
				java.nio.file.Path saved = java.nio.file.Paths.get(s.localDir).toAbsolutePath().normalize();
				// If the saved path exists and contains a .git, use it directly.
				try {
					if (java.nio.file.Files.exists(saved) && java.nio.file.Files.exists(saved.resolve(".git"))) {
						try {
							org.eclipse.jgit.api.Git g = org.eclipse.jgit.api.Git.open(saved.toFile());
							java.util.Properties cfg = VglRepo.loadConfig(saved);
							VglRepo vglRepo = new VglRepo(g, cfg);
							repoRoot = saved;
							Map<String,String> meta = new HashMap<>();
							try { String lb = cfg.getProperty("local.branch", null); if (lb != null) meta.put("local.branch", lb); } catch (Exception ignore) {}
							return new RepoResolution(g, vglRepo, repoRoot, RepoResolution.ResolutionKind.FOUND_BOTH, false, false, Utils.isInteractive(), null, meta);
						} catch (Exception ignore) {
							// Fall through to normal resolution if opening fails
						}
					} else {
						// Persisted state points at a path that either does not exist or has no .git
						String statePath = VglStateStore.getDefaultStatePath().toString();
						String msg = "VGL persisted state '" + statePath + "' references '" + saved + "' but no Git repository was found there.";
						Map<String,String> meta = new HashMap<>();
						meta.put("vgl.state.path", statePath);
						meta.put("vgl.state.referenced", saved.toString());
						return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg + " Delete or fix the state file and run 'vgl create <path>' to recreate.", meta);
					}
				} catch (Exception ignore) {}
			}
		} catch (Exception ignore) {}

		// Discover repo root and state
		Path searchRoot = start.toAbsolutePath().normalize();
		Path foundRoot = null;
		boolean foundGit = false;
		boolean foundVgl = false;
		while (searchRoot != null) {
			if (java.nio.file.Files.exists(searchRoot.resolve(".git"))) {
				foundGit = true;
				foundRoot = searchRoot;
			}
			if (java.nio.file.Files.exists(searchRoot.resolve(".vgl"))) {
				foundVgl = true;
				if (foundRoot == null) foundRoot = searchRoot;
			}
			if (foundGit || foundVgl) break;
			searchRoot = searchRoot.getParent();
		}

		// Centralized orphaned .vgl detection
		if (foundVgl && !foundGit) {
			String msg = "Found .vgl but no .git directory; deleting orphaned .vgl.\nHint: Run 'vgl create' to initialize a new repo here.";
			try { java.nio.file.Files.deleteIfExists(foundRoot.resolve(".vgl")); } catch (Exception ignore) {}
			return new RepoResolution(null, null, foundRoot, RepoResolution.ResolutionKind.FOUND_VGL_ONLY, false, false, Utils.isInteractive(), msg, new HashMap<>());
		}
		if (!foundVgl && !foundGit) {
			String msg = "WARNING: No VGL repository found in this directory or any parent.\nHint: Run 'vgl create' to initialize a new repo here.";
			return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg, new HashMap<>());
		}
		if (foundGit && !foundVgl) {
			String msg = "Found Git repository but no .vgl (non-interactive)";
			return new RepoResolution(null, null, foundRoot, RepoResolution.ResolutionKind.FOUND_GIT_ONLY, false, false, Utils.isInteractive(), msg, new HashMap<>());
		}
		// If both found, try to open and validate
		if (foundGit && foundVgl) {
			try {
				org.eclipse.jgit.api.Git g = org.eclipse.jgit.api.Git.open(foundRoot.toFile());
				java.util.Properties cfg = VglRepo.loadConfig(foundRoot);
				VglRepo vglRepo = new VglRepo(g, cfg);
				Map<String,String> meta = new HashMap<>();
				try { String lb = cfg.getProperty("local.branch", null); if (lb != null) meta.put("local.branch", lb); } catch (Exception ignore) {}
				return new RepoResolution(g, vglRepo, foundRoot, RepoResolution.ResolutionKind.FOUND_BOTH, false, false, Utils.isInteractive(), null, meta);
			} catch (Exception e) {
				String msg = "Failed to open Git/VGL repo at '" + foundRoot + "': " + e.getMessage();
				return new RepoResolution(null, null, foundRoot, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), msg, new HashMap<>());
			}
		}

		// End of new centralized repo state logic. No legacy code should follow.
		// Defensive: fallback return to satisfy compiler (should never be reached)
		return new RepoResolution(null, null, null, RepoResolution.ResolutionKind.NONE, false, false, Utils.isInteractive(), "Unknown repository state.", new HashMap<>());
	}

	// Backwards-compatible helpers
	public static VglRepo resolveVglRepoForCommand() throws IOException {
		RepoResolution r = resolveForCommand();
		return r.getVglRepo();
	}

	public static VglRepo resolveVglRepoForCommand(java.nio.file.Path start) throws IOException {
		RepoResolution r = resolveForCommand(start);
		return r.getVglRepo();
	}

	public static Git resolveGitRepoForCommand() throws IOException {
		RepoResolution r = resolveForCommand();
		return r.getGit();
	}

	public static Git resolveGitRepoForCommand(java.nio.file.Path start) throws IOException {
		RepoResolution r = resolveForCommand(start);
		return r.getGit();
	}
}
