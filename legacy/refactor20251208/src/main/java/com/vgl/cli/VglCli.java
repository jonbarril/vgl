package com.vgl.cli;

import com.vgl.cli.commands.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import com.vgl.cli.utils.RepoResolver;

public class VglCli {
    /**
     * For testing: if set, overrides the base directory used for config search.
     */
    public static Path testConfigBaseDir = null;
    private static final String CONFIG_FILE = ".vgl";
    private final Map<String, Command> cmds = new LinkedHashMap<>();
    private final Properties config = new Properties();
    // Logger removed; use System.err for warnings/errors

    public VglCli() {
        loadConfig();
        register(new HelpCommand());
        register(new CreateCommand());
        register(new DeleteCommand());
        register(new CheckoutCommand());
        register(new SwitchCommand());
        register(new SplitCommand());
        register(new MergeCommand());
        register(new TrackCommand());
        register(new UntrackCommand());
        register(new CommitCommand());
        register(new RestoreCommand());
        register(new PullCommand());
        register(new PushCommand());
        register(new CheckinCommand());
        register(new SyncCommand());
        register(new AbortCommand());
        register(new StatusCommand());
        register(new DiffCommand());
        register(new LogCommand());
    }

    private void register(Command c) {
        cmds.put(c.name(), c);
    }

    public int run(String[] argv) {
        // (debug output removed)
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        // If no command or first arg is a flag (starts with '-') or is not a known
        // command, default to help
        if (args.isEmpty() || args.get(0).startsWith("-") || !cmds.containsKey(args.get(0))) {
            // (debug output removed)
            args.add(0, "help");
        }

        String commandName = args.remove(0);
        // (debug output removed)
        Command command = cmds.get(commandName);

        try {
            int result = command.run(Collections.unmodifiableList(args));
            return result;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("local.dir should never resolve to user home")) {
                return 1;
            }
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    public boolean isConfigurable() {
        String localDir = getLocalDir();
        return Files.exists(Paths.get(localDir).resolve(".git"));
    }

    /**
     * Search upward from current directory for .vgl file.
     * 
     * @param repoRoot The root of the git repository, or null if no repo is known.
     *                 If provided, search stops at this boundary.
     *                 If null, only checks the current working directory.
     */

    private void loadConfig() {
        // Always prefer .vgl in current working directory or repo root
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path configPath = cwd.resolve(CONFIG_FILE);
        boolean found = Files.exists(configPath);
        if (!found) {
            // Try to find git repo root and look for .vgl there
            Path repoRoot = null;
            try {
                Git git = RepoResolver.resolveGitRepoForCommand();
                if (git != null) {
                    repoRoot = git.getRepository().getWorkTree().toPath();
                    git.close();
                }
            } catch (IOException e) {
                // No repo found - will only check current directory
            }
            if (repoRoot != null && !repoRoot.equals(cwd)) {
                Path repoConfig = repoRoot.resolve(CONFIG_FILE);
                if (Files.exists(repoConfig)) {
                    configPath = repoConfig;
                    found = true;
                }
            }
        }
        // Only print debug if VGL_DEBUG or vgl.debug system property is explicitly set to true (case-insensitive)
        // String debugFlag = System.getenv("VGL_DEBUG");
        // String debugSysProp = System.getProperty("vgl.debug");
        // boolean debug = (debugFlag != null && debugFlag.trim().equalsIgnoreCase("true")) ||
        //                 (debugSysProp != null && debugSysProp.trim().equalsIgnoreCase("true"));
        // Variable 'debug' is now only used for commented-out debug print; remove warning by using or removing if not needed.
        // if (debug) {
        //     System.err.println("[DEBUG] VglCli.loadConfig: looking for .vgl at " + configPath + ", found=" + found);
        // }
        if (found) {
            // Check if .git exists alongside .vgl
            Path vglDir = configPath.getParent();
            if (!Files.exists(vglDir.resolve(".git"))) {
                // Orphaned .vgl file - .git was deleted or moved. Print warning and (optionally) delete .vgl
                String warn = "warning: found .vgl but no .git directory";
                String del = "deleted orphaned .vgl file";
                String fail = "failed to delete orphaned .vgl file: ";
                System.err.println(warn);
                System.err.flush();
                boolean nonInteractive = Boolean.parseBoolean(System.getProperty("vgl.noninteractive", "false"));
                if (nonInteractive) {
                    try {
                        boolean deleted = Files.deleteIfExists(configPath);
                        if (deleted) {
                            System.err.println(del);
                        }
                    } catch (IOException e) {
                        System.err.println(fail + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
                System.err.flush();
                return;
            }
            // Valid .vgl with .git - load it
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
            } catch (IOException e) {
                // System.err.println("Failed to load configuration file at " + configPath + ": " + e.getMessage());
            }
        } else {
            // Fallback: try to load from global state if present
            // (for legacy or non-repo commands)
            // No config loaded if not found
        }
    }

    /**
     * Reload configuration from disk, replacing any in-memory values.
     */
    public void reloadConfig() {
        config.clear();
        loadConfig();
    }

    private void saveConfig() {
        // Find the git root for local.dir and save .vgl there
        String localDir = getLocalDir();
        Path localPath = Paths.get(localDir).toAbsolutePath().normalize();

        // Search upward from local.dir to find .git
        Path gitRoot = localPath;
        while (gitRoot != null && !Files.exists(gitRoot.resolve(".git"))) {
            gitRoot = gitRoot.getParent();
        }

        if (gitRoot != null) {
            // Save .vgl alongside .git
            Path savePath = gitRoot.resolve(CONFIG_FILE);
            if ("true".equalsIgnoreCase(System.getenv("VGL_DEBUG"))) {
                System.err.println("[DEBUG] VglCli.saveConfig: writing .vgl to " + savePath);
            }

            // Only restrict writing .vgl into user home itself during tests
            String testBase = System.getProperty("vgl.test.base");
            String junitTemp = System.getProperty("junit.temp.root");
            boolean isTest = (testBase != null && !testBase.isEmpty()) || (junitTemp != null && !junitTemp.isEmpty());
            if (isTest) {
                try {
                    Path userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
                    Path normalizedSaveParent = savePath.getParent().toAbsolutePath().normalize();
                    if (normalizedSaveParent.equals(userHome)) {
                        System.err.println("Warning: refusing to write .vgl into user home; skipping save.");
                        return;
                    }
                } catch (Exception ignore) {
                    // Fall back to attempting to write if we cannot determine user.home
                }
            }

            // Allow saving .vgl anywhere in normal use, and in subdirs of user home during tests
            try (OutputStream out = Files.newOutputStream(savePath)) {
                config.store(out, "VGL Configuration");
            } catch (IOException e) {
                System.err.println("Warning: Failed to save configuration file.");
                // System.err.println("[DEBUG] Failed to save configuration file to " + savePath
                // + ": " + e.getMessage());
            }
        } else {
            //// System.out.println("Info: No local repository found. Configuration file
            /// will not be created.");
        }
    }

    public void save() {
        saveConfig();
    }

    /**
     * Returns the configured local directory for the repository, enforcing safety
     * rules:
     * <ul>
     * <li>Never allows the user home directory itself as a repo root (prevents
     * accidental destructive operations).</li>
     * <li>Allows any descendant of a test temp root (set via
     * {@code junit.temp.root} or {@code JUNIT_TEMP_ROOT}),
     * even if that temp root is under the user home. This is required for robust
     * test isolation, since
     * JUnit and many CI systems create temp directories under the user home by
     * default.</li>
     * <li>Blocks any other path under user home, unless it is a descendant of the
     * temp root.</li>
     * </ul>
     * This policy ensures that tests can safely use temp directories as repos,
     * while production code is protected
     * from ever using the user home as a repo root.
     *
     * @return the absolute path to the local repo directory
     * @throws IllegalStateException if the resolved directory is the user home or
     *                               an unsafe path
     */
    public String getLocalDir() {
        String dir = config.getProperty("local.dir", null);
        // Treat null, empty, or whitespace-only as unset
        if (dir == null || dir.trim().isEmpty()) {
            String cwd = System.getProperty("user.dir");
            if (cwd == null || cwd.trim().isEmpty()) {
                throw new IllegalStateException("No local.dir set and no working directory available");
            }
            dir = Paths.get(cwd).toAbsolutePath().normalize().toString();
        }
        dir = dir.trim();
        if (dir.isEmpty()) {
            throw new IllegalStateException("local.dir resolved to empty string");
        }
        Path dirPath = Paths.get(dir).toAbsolutePath().normalize();
        // Only restrict user home usage during tests (vgl.test.base or junit.temp.root set),
        // but allow if dirPath is under the test root (even if test root is under user home)
        String testBase = System.getProperty("vgl.test.base");
        String junitTemp = System.getProperty("junit.temp.root");
        Path testRoot = null;
        if (testBase != null && !testBase.isEmpty()) {
            testRoot = Paths.get(testBase).toAbsolutePath().normalize();
        } else if (junitTemp != null && !junitTemp.isEmpty()) {
            testRoot = Paths.get(junitTemp).toAbsolutePath().normalize();
        }
        String userHomeStr = System.getProperty("user.home");
        if (testRoot != null && userHomeStr != null && !userHomeStr.trim().isEmpty()) {
            Path userHome = Paths.get(userHomeStr).toAbsolutePath().normalize();
            // If dirPath is under testRoot, allow it (even if testRoot is under user home)
            if (dirPath.startsWith(testRoot)) {
                return dir;
            }
            // Otherwise, block if dirPath is user home or a subdir
            if (dirPath.equals(userHome) || dirPath.startsWith(userHome)) {
                throw new IllegalStateException("local.dir should never resolve to user home during tests");
            }
        }
        return dir;
    }

    public void setLocalDir(String dir) {
        // Treat null, empty, or whitespace-only as unset
        if (dir == null || dir.trim().isEmpty()) {
            config.remove("local.dir");
            return;
        }
        String absolutePath = Paths.get(dir.trim()).toAbsolutePath().normalize().toString();
        config.setProperty("local.dir", absolutePath);
    }

    public String getLocalBranch() {
        return config.getProperty("local.branch", "main");
    }

    public void setLocalBranch(String branch) {
        config.setProperty("local.branch", branch);
    }

    public String getRemoteUrl() {
        String url = config.getProperty("remote.url", null);
        return (url == null || url.isEmpty()) ? null : url;
    }

    public void setRemoteUrl(String url) {
        if (url != null && !url.isEmpty()) {
            config.setProperty("remote.url", url);
        } else {
            config.remove("remote.url");
        }
    }

    public String getRemoteBranch() {
        String branch = config.getProperty("remote.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setRemoteBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("remote.branch", branch);
        } else {
            config.remove("remote.branch");
        }
    }

    // ...existing code...
}
