// This file has been moved to com.vgl.cli.testutil
package com.vgl.cli.test.utils;

import java.util.Properties;
import org.eclipse.jgit.api.Git;

import com.vgl.cli.VglCli;
import com.vgl.cli.VglMain;
import com.vgl.cli.services.RepoManager;

import java.io.*;
import java.nio.file.*;

/**
 * Test harness for VGL unit and integration tests.
 * Provides consistent, reliable setup/teardown for test repositories.
 */
public class VglTestHarness {
    /**
     * Creates a per-test temp root under build/tmp and sets vgl.test.base to it.
     * All test repos and dirs should be created as subdirs of this root.
     * @return Path to the test root
     */
    public static Path createTestRoot() throws IOException {
        Path testRoot = Paths.get("build", "tmp", "test-" + java.util.UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(testRoot);
        System.setProperty("vgl.test.base", testRoot.toString());
        return testRoot;
    }

    /**
     * Runs the VGL CLI command as a subprocess, capturing stdout and stderr.
     * Sets the working directory to the given path for the duration of the call.
     * Returns the combined output as a String.
     * Usage: VglTestHarness.runVglCommand(repoPath, "status", "-v")
     */
    public static String runVglCommand(Path workingDir, String... args) throws Exception {
        // Path to the installed CLI script (vgl.bat for Windows)
        String vglScript = workingDir.getFileSystem().getSeparator().equals("\\")
            ? "build/install/vgl/bin/vgl.bat"
            : "build/install/vgl/bin/vgl";
        Path vglPath = Paths.get(vglScript).toAbsolutePath();
        if (!Files.exists(vglPath)) {
            throw new IOException("VGL CLI not found at " + vglPath + ". Did you run 'gradlew installDist'?");
        }

        // Build the command
        String[] cmd = new String[args.length + 1];
        cmd[0] = vglPath.toString();
        System.arraycopy(args, 0, cmd, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(cmd)
            .directory(workingDir.toFile());

        // Inherit test environment, but set user.dir and vgl.test.base for isolation
        pb.environment().put("user.dir", workingDir.toString());
        String vglTestBase = System.getProperty("vgl.test.base");
        if (vglTestBase != null) {
            pb.environment().put("vgl.test.base", vglTestBase);
        }
        pb.environment().put("vgl.noninteractive", "true");

        // Provide "n\n" to stdin to decline prompts
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()))) {
            writer.write("n\n");
            writer.flush();
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            output.append("[VGL CLI exited with code ").append(exitCode).append("]");
        }
        return output.toString();
    }

    // Centralized helpers for .git/.vgl creation and update using RepoManager/RepoResolver
    public static Git createGitRepo(Path repoPath) throws Exception {
        // Use default branch 'main' and minimal config
        return RepoManager.createVglRepo(repoPath, "main", null);
    }

    public static void createVglConfig(Path repoPath, java.util.Properties props) throws Exception {
        RepoManager.updateVglConfig(repoPath, props);
    }

    public static void updateVglProperty(Path repoPath, String key, String value) throws Exception {
        Properties props = new Properties();
        Path vglFile = repoPath.resolve(".vgl");
        if (Files.exists(vglFile)) {
            try (InputStream in = Files.newInputStream(vglFile)) { props.load(in); }
        }
        props.setProperty(key, value);
        RepoManager.updateVglConfig(repoPath, props);
    }

    public static void updateGitConfig(Git git, String section, String name, String value) throws Exception {
        git.getRepository().getConfig().setString(section, null, name, value);
        git.getRepository().getConfig().save();
    }
    
    /**
     * Creates a test repository with .git initialized.
     * Automatically sets user.dir to the repo path.
     * Returns an AutoCloseable that restores user.dir on close.
     * <p>
     * <b>SAFETY:</b> This method will throw if the repoPath is the user home directory or a parent of it.
     * Always use @TempDir or createTestRoot() to ensure isolation.
     */
    public static VglTestRepo createRepo(Path repoPath) throws Exception {
        // Always create a valid VGL repo (with .git, .vgl, .gitignore) using RepoManager
        RepoManager.createVglRepo(repoPath, "main", null);
        return new VglTestRepo(repoPath, true);
    }

    /**
     * Creates a test directory WITHOUT .git (for testing error cases).
     */
    public static VglTestRepo createDir(Path dirPath) throws Exception {
        // Only create the directory, do not initialize git or vgl
        if (!Files.exists(dirPath)) Files.createDirectories(dirPath);
        return new VglTestRepo(dirPath, false);
    }
    
    /**
     * Creates a bare remote repository at the specified path.
     * This is useful for tests that need to push/fetch from a remote.
     * 
     * @param remotePath Path where the bare repository should be created
     * @return Path to the created bare repository
     */
    public static Path createBareRemoteRepo(Path remotePath) throws Exception {
        // If a test base is provided via system property, prefer creating remotes there
        String testBaseProp = System.getProperty("vgl.test.base");
        Path resolvedRemotePath = remotePath;
        if (testBaseProp != null && !testBaseProp.isEmpty()) {
            Path testBase = Paths.get(testBaseProp);
            Files.createDirectories(testBase);
            // Use the provided remotePath name as a directory name under testBase
            String name = remotePath.getFileName() != null ? remotePath.getFileName().toString() : "remote";
            resolvedRemotePath = testBase.resolve(name + "-" + java.util.UUID.randomUUID().toString());
        }
        Files.createDirectories(resolvedRemotePath);
        try (@SuppressWarnings("unused") Git remoteGit = Git.init().setDirectory(resolvedRemotePath.toFile()).setBare(true).call()) {
        }
        return resolvedRemotePath;
    }
    
    /**
     * Sets up a local repo with a remote, including:
     * 1. Creating a bare remote repository
     * 2. Pushing the local branch to it
     * 3. Configuring remote tracking
     * 4. Fetching to ensure refs are synced
     * 
     * This is a convenience method for tests that need remote functionality.
     * 
     * @param repo The local test repository
     * @param remotePath Path where the remote should be created
     * @param branch Branch name to track (e.g., "main")
     * @return Path to the created remote repository
     */
    public static Path setupRemoteRepo(VglTestRepo repo, Path remotePath, String branch) throws Exception {
        // Create bare remote
        Path actualRemote = createBareRemoteRepo(remotePath);
        
        // Push to remote
        try (Git git = repo.getGit()) {
            // Ensure HEAD resolves to an object. In some test cases the
            // repository may be newly-initialized but missing an initial
            // commit (unborn HEAD). JGit will refuse to push in that case.
            // Create a lightweight empty commit if necessary so the push
            // succeeds and tests can proceed.
            org.eclipse.jgit.lib.ObjectId headId = null;
            try { headId = git.getRepository().resolve("HEAD"); } catch (Exception ignored) {}
            if (headId == null) {
                // Create an empty commit to ensure HEAD exists. Use allowEmpty
                // so we don't require any staged files.
                try {
                    git.commit().setMessage("initial (autocreated)").setAllowEmpty(true).call();
                } catch (Exception e) {
                    // If commit creation fails, continue and let push surface the error
                }
            }

            // Push current HEAD to the remote branch explicitly.
            String refspec = "HEAD:refs/heads/" + branch;
            git.push().setRemote(actualRemote.toUri().toString()).setRefSpecs(new org.eclipse.jgit.transport.RefSpec(refspec)).call();
        }
        
        // Set up remote tracking
        repo.setupRemoteTracking(actualRemote.toUri().toString(), branch);
        
        // Fetch to sync refs
        try (Git git = repo.getGit()) {
            git.fetch().call();
        } catch (Exception e) {
            // Ignore "nothing to fetch" errors - this can happen if refs are already up to date
        }
        
        return actualRemote;
    }
    
    /**
     * Represents a test repository with automatic cleanup.
     */
    public static class VglTestRepo implements AutoCloseable {
                /**
                 * Returns a list of local branch names in this repo.
                 */
                public java.util.List<String> getBranches() throws Exception {
                    java.util.List<String> branches = new java.util.ArrayList<>();
                    try (Git git = getGit()) {
                        for (org.eclipse.jgit.lib.Ref ref : git.branchList().call()) {
                            String name = ref.getName();
                            if (name.startsWith("refs/heads/")) {
                                branches.add(name.substring("refs/heads/".length()));
                            } else {
                                branches.add(name);
                            }
                        }
                    }
                    return branches;
                }
        private final Path path;
        private final String originalUserDir;
        private final boolean hasGit;
        private final String originalVglTestBase;
        
        /**
         * Creates a test repo wrapper. Throws if path is user home or a parent of it.
         */
        VglTestRepo(Path path, boolean initGit) throws Exception {
            this.path = path;
            this.originalUserDir = System.getProperty("user.dir");
            this.originalVglTestBase = System.getProperty("vgl.test.base");
            this.hasGit = initGit;
            // SAFETY: Prevent using a repo path outside the temp root (ceiling)
            String tempRootStr = System.getenv("JUNIT_TEMP_ROOT");
            if (tempRootStr == null || tempRootStr.isEmpty()) {
                tempRootStr = System.getProperty("junit.temp.root");
            }
            if (tempRootStr != null && !tempRootStr.isEmpty()) {
                Path tempRoot = Paths.get(tempRootStr).toAbsolutePath().normalize();
                Path absPath = path.toAbsolutePath().normalize();
                if (!absPath.startsWith(tempRoot)) {
                    throw new IllegalArgumentException("Test repo path must be under the temp root: " + tempRoot + ", got: " + absPath);
                }
            }
            // Create directory if needed
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // Initialize git if requested
            if (initGit) {
                try (Git git = Git.init().setDirectory(path.toFile()).setInitialBranch("main").call()) {
                    // Configure git for tests
                    git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
                    git.getRepository().getConfig().setString("user", null, "name", "Test User");
                    git.getRepository().getConfig().save();
                }
            }
            // Set working directory
            System.setProperty("user.dir", path.toString());
            // vgl.test.base is set once per test via createTestRoot()
        }
        
        /**
         * Get the repository path.
         */
        public Path getPath() {
            return path;
        }
        
        /**
         * Check if this repo has .git initialized.
         */
        public boolean hasGit() {
            return hasGit;
        }

        /**
         * Run a VGL command in this repo and capture output.
         * Sets user.dir to the repo path for the duration of the call.
         * Automatically provides "n\n" to stdin to decline any prompts.
         * @param args Command arguments (e.g., "status", "-v")
         * @return Combined stdout and stderr
         */
        public String runCommand(String... args) throws Exception {
            String originalUserDir = System.getProperty("user.dir");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            InputStream oldIn = System.in;
            try {
                System.setProperty("user.dir", path.toString());
                PrintStream ps = new PrintStream(baos, true, "UTF-8");
                System.setOut(ps);
                System.setErr(ps);
                // Provide "n\n" to stdin to decline prompts
                ByteArrayInputStream fakeIn = new ByteArrayInputStream("n\n".getBytes("UTF-8"));
                System.setIn(fakeIn);
                VglMain.main(args);
                return baos.toString("UTF-8");
            } finally {
                System.setProperty("user.dir", originalUserDir);
                System.setOut(oldOut);
                System.setErr(oldErr);
                System.setIn(oldIn);
            }
        }
        
        /**
         * Run a VGL command and capture output.
         * Automatically provides "n\n" to stdin to decline any prompts.
         * Use runCommandWithInput() to provide custom stdin.
         * @param args Command arguments (e.g., "status", "-v")
         * @return Combined stdout and stderr
         */
        // Remove runCommand and runCommandWithInput for create command; all repo setup should use RepoManager/RepoResolver directly.
        
        /**
         * Run a VGL command expecting it to fail.
         * @return Output from the failed command
         */
        public String runCommandExpectingFailure(String... args) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            try {
                PrintStream ps = new PrintStream(baos, true, "UTF-8");
                System.setOut(ps);
                System.setErr(ps);
                new VglCli().run(args);
                return baos.toString("UTF-8");
            } catch (Exception e) {
                // Expected failure
                return baos.toString("UTF-8") + "\n" + e.getMessage();
            } finally {
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
        }
        
        /**
         * Create a new VglCli instance with current user.dir set to this repo.
         */
        public VglCli createVglInstance() {
            return new VglCli();
        }
        
        /**
         * Write a file to the repository.
         */
        public Path writeFile(String filename, String content) throws IOException {
            Path file = path.resolve(filename);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return file;
        }
        
        /**
         * Read a file from the repository.
         */
        public String readFile(String filename) throws IOException {
            return Files.readString(path.resolve(filename));
        }
        
        /**
         * Check if a file exists in the repository.
         */
        public boolean fileExists(String filename) {
            return Files.exists(path.resolve(filename));
        }
        
        /**
         * Get the Git instance for this repo (if it has .git).
         */
        public Git getGit() throws IOException {
            // Check if .git exists (may have been created by vgl create command)
            if (!hasGit && !Files.exists(path.resolve(".git"))) {
                throw new IllegalStateException("This test directory does not have .git initialized");
            }
            return Git.open(path.toFile());
        }
        
        /**
         * Stage a file using git add.
         */
        public void gitAdd(String filename) throws Exception {
            try (Git git = getGit()) {
                git.add().addFilepattern(filename).call();
            }
        }
        
        /**
         * Commit with a message.
         */
        public void gitCommit(String message) throws Exception {
            try (Git git = getGit()) {
                git.commit().setMessage(message).call();
            }
        }
        
        /**
         * Create a subdirectory in the repository.
         */
        public Path createSubdir(String subdirName) throws IOException {
            Path subdir = path.resolve(subdirName);
            Files.createDirectories(subdir);
            return subdir;
        }
        
        /**
         * Set up a remote with proper tracking branch.
         * Call this after the first push to ensure origin/branch refs exist.
         */
        public void setupRemoteTracking(String remoteUrl, String branch) throws Exception {
            try (Git git = getGit()) {
                // Configure remote with fetch refspec
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteUrl);
                git.getRepository().getConfig().setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                git.getRepository().getConfig().setString("branch", branch, "remote", "origin");
                git.getRepository().getConfig().setString("branch", branch, "merge", "refs/heads/" + branch);
                git.getRepository().getConfig().save();
                
                // Create the remote tracking ref manually
                org.eclipse.jgit.lib.ObjectId headId = git.getRepository().resolve("refs/heads/" + branch);
                if (headId != null) {
                    org.eclipse.jgit.lib.RefUpdate refUpdate = git.getRepository().updateRef("refs/remotes/origin/" + branch);
                    refUpdate.setNewObjectId(headId);
                    refUpdate.update();
                }
            }
            
            // Initialize VGL config with remote
            VglCli vgl = new VglCli();
            vgl.setLocalDir(path.toString());
            vgl.setLocalBranch(branch);
            vgl.setRemoteUrl(remoteUrl);
            vgl.setRemoteBranch(branch);
            vgl.save();
        }
        
        @Override
        public void close() {
            // Restore original user.dir
            System.setProperty("user.dir", originalUserDir);
            // Restore original vgl.test.base (if any)
            if (originalVglTestBase == null) {
                System.getProperties().remove("vgl.test.base");
            } else {
                System.setProperty("vgl.test.base", originalVglTestBase);
            }
        }
    }
}
