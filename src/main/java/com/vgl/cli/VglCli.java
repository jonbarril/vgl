package com.vgl.cli;

import com.vgl.cli.commands.StatusCommand;
import com.vgl.cli.commands.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import com.vgl.cli.utils.RepoResolver;

public class VglCli {
    private static final String CONFIG_FILE = ".vgl";
    private final Map<String, Command> cmds = new LinkedHashMap<>();
    private final Properties config = new Properties();
    private static final Logger LOG = LoggerFactory.getLogger(VglCli.class);

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
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        // If no command or first arg is a flag (starts with '-') or is not a known command, default to help
        if (args.isEmpty() || args.get(0).startsWith("-") || !cmds.containsKey(args.get(0))) {
            args.add(0, "help");
        }

        String commandName = args.remove(0);
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
            LOG.debug("Top-level exception while running command", e);
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
    private Path findConfigFile(Path repoRoot) {
        // Use user.dir property (respects test environment)
        String userDir = System.getProperty("user.dir");
        Path current = Paths.get(userDir).toAbsolutePath().normalize();
        
        // If no repo root provided, only check current directory
        if (repoRoot == null) {
            Path configPath = current.resolve(CONFIG_FILE);
            return Files.exists(configPath) ? configPath : null;
        }
        
        // Normalize repo root for comparison
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        
        // Search from current dir up to repo root for .vgl
        while (current != null) {
            Path configPath = current.resolve(CONFIG_FILE);
            if (Files.exists(configPath)) {
                return configPath;
            }
            
            // Stop at repo root
            if (current.equals(normalizedRoot)) {
                break;
            }
            
            current = current.getParent();
        }
        
        return null; // Not found
    }

    private void loadConfig() {
        // Find git repo root first, then search for .vgl within that boundary
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
        
        // Search upward for .vgl file (bounded by repo root)
        Path configPath = findConfigFile(repoRoot);
        if (configPath != null && Files.exists(configPath)) {
            // If tests set `vgl.test.base`, restrict loading to that base (used by hermetic tests).
            // For normal end-user runs (no property set) do not restrict loading.
            String testBaseProp = System.getProperty("vgl.test.base");
            if (testBaseProp != null && !testBaseProp.isEmpty()) {
                Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
                Path parent = configPath.getParent().toAbsolutePath().normalize();
                boolean allowed = false;
                try {
                    Path testBase = Paths.get(testBaseProp).toAbsolutePath().normalize();
                    if (parent.startsWith(testBase)) allowed = true;
                } catch (Exception ignore) {}
                if (!allowed && parent.equals(current)) allowed = true;
                // Allow loading configs from the system temp dir (JUnit @TempDir uses java.io.tmpdir)
                try {
                    String tmp = System.getProperty("java.io.tmpdir");
                    if (tmp != null && !tmp.isEmpty()) {
                        Path tmpDir = Paths.get(tmp).toAbsolutePath().normalize();
                        if (parent.startsWith(tmpDir)) allowed = true;
                    }
                } catch (Exception ignore) {}
                if (!allowed) {
                    // Skip loading configs outside the test base or current directory (test-only behavior)
                    LOG.debug("Skipping loading .vgl outside test base or working directory: {}", configPath);
                    return;
                }
            }
            // Check if .git exists alongside .vgl
            Path vglDir = configPath.getParent();
            if (!Files.exists(vglDir.resolve(".git"))) {
                // Orphaned .vgl file - .git was deleted or moved. This is a user-facing
                // condition but we keep loadConfig quiet: callers (commands) decide how
                // to surface messages to users. Log details at DEBUG for troubleshooting.
                LOG.debug("Found .vgl at {} but no .git directory; treating as orphaned.", configPath);
                return;
            }
            
            // Valid .vgl with .git - load it
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
            } catch (IOException e) {
                LOG.debug("Failed to load configuration file at {}.", configPath, e);
            }
        } else {
            //// System.out.println("Info: No configuration file found. Defaults will
            /// be used.");
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

            // Defensive check: avoid writing .vgl into the real user home directory
            try {
                Path userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
                Path normalizedSaveParent = savePath.getParent().toAbsolutePath().normalize();
                if (normalizedSaveParent.equals(userHome) || normalizedSaveParent.startsWith(userHome)) {
                    System.err.println("Warning: refusing to write .vgl into user home; skipping save.");
                    return;
                }
            } catch (Exception ignore) {
                // Fall back to attempting to write if we cannot determine user.home
            }

            // Note: Only refuse writes into the real user home directory above.
            // Allow saving .vgl into repository/workspace locations used by tests
            // (test frameworks often change working dir to temp locations).

            try (OutputStream out = Files.newOutputStream(savePath)) {
                config.store(out, "VGL Configuration");
            } catch (IOException e) {
                System.err.println("Warning: Failed to save configuration file.");
                LOG.debug("Failed to save configuration file to {}", savePath, e);
            }
        } else {
            //// System.out.println("Info: No local repository found. Configuration file
            /// will not be created.");
        }
    }

    public void save() {
        saveConfig();
    }

    public String getLocalDir() {
        String dir = config.getProperty("local.dir", null);
        if (dir == null || dir.isEmpty()) {
            // Default to current working directory, never user home
            String cwd = System.getProperty("user.dir");
            if (cwd == null || cwd.isEmpty()) {
                throw new IllegalStateException("No local.dir set and no working directory available");
            }
            return Paths.get(cwd).toAbsolutePath().normalize().toString();
        }
        // Defensive: never allow user home as a fallback
        Path dirPath = Paths.get(dir).toAbsolutePath().normalize();
        Path userHome = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (dirPath.equals(userHome) || dirPath.startsWith(userHome)) {
            throw new IllegalStateException("local.dir should never resolve to user home");
        }
        return dir;
    }

    public void setLocalDir(String dir) {
        // Always store absolute paths
        String absolutePath = Paths.get(dir).toAbsolutePath().normalize().toString();
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

    // Jump state management - stores previous context for toggle
    public String getJumpLocalDir() {
        String dir = config.getProperty("jump.local.dir", null);
        return (dir == null || dir.isEmpty()) ? null : dir;
    }

    public void setJumpLocalDir(String dir) {
        if (dir != null && !dir.isEmpty()) {
            // Always store absolute paths
            String absolutePath = Paths.get(dir).toAbsolutePath().normalize().toString();
            config.setProperty("jump.local.dir", absolutePath);
        } else {
            config.remove("jump.local.dir");
        }
    }

    public String getJumpLocalBranch() {
        String branch = config.getProperty("jump.local.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setJumpLocalBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("jump.local.branch", branch);
        } else {
            config.remove("jump.local.branch");
        }
    }

    public String getJumpRemoteUrl() {
        String url = config.getProperty("jump.remote.url", null);
        return (url == null || url.isEmpty()) ? null : url;
    }

    public void setJumpRemoteUrl(String url) {
        if (url != null && !url.isEmpty()) {
            config.setProperty("jump.remote.url", url);
        } else {
            config.remove("jump.remote.url");
        }
    }

    public String getJumpRemoteBranch() {
        String branch = config.getProperty("jump.remote.branch", null);
        return (branch == null || branch.isEmpty()) ? null : branch;
    }

    public void setJumpRemoteBranch(String branch) {
        if (branch != null && !branch.isEmpty()) {
            config.setProperty("jump.remote.branch", branch);
        } else {
            config.remove("jump.remote.branch");
        }
    }

    /**
     * Check if jump state exists (at least one jump property is set)
     */
    public boolean hasJumpState() {
        return getJumpLocalDir() != null || getJumpLocalBranch() != null;
    }
}
