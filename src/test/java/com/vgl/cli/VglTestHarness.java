package com.vgl.cli;

import org.eclipse.jgit.api.Git;
import java.io.*;
import java.nio.file.*;

/**
 * Test harness for VGL unit and integration tests.
 * Provides consistent, reliable setup/teardown for test repositories.
 * 
 * Usage:
 * <pre>
 * {@literal @}Test
 * void myTest(@TempDir Path tmp) throws Exception {
 *     try (VglTestRepo repo = VglTestHarness.createRepo(tmp)) {
 *         String output = repo.runCommand("status");
 *         assertThat(output).contains("LOCAL");
 *     }
 * }
 * </pre>
 */
public class VglTestHarness {
    
    /**
     * Creates a test repository with .git initialized.
     * Automatically sets user.dir to the repo path.
     * Returns an AutoCloseable that restores user.dir on close.
     */
    public static VglTestRepo createRepo(Path repoPath) throws Exception {
        return new VglTestRepo(repoPath, true);
    }
    
    /**
     * Creates a test directory WITHOUT .git (for testing error cases).
     */
    public static VglTestRepo createDir(Path dirPath) throws Exception {
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
        private final Path path;
        private final String originalUserDir;
        private final boolean hasGit;
        private final String originalVglTestBase;
        
        VglTestRepo(Path path, boolean initGit) throws Exception {

            this.path = path;
            this.originalUserDir = System.getProperty("user.dir");
            this.originalVglTestBase = System.getProperty("vgl.test.base");
            this.hasGit = initGit;
            
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
            // During tests, set the vgl.test.base ceiling so repo discovery cannot
            // escape the per-test temporary directory. Restore on close.
            try {
                System.setProperty("vgl.test.base", path.toAbsolutePath().toString());
            } catch (Exception ignored) {}
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
         * Run a VGL command and capture output.
         * Automatically provides "n\n" to stdin to decline any prompts.
         * Use runCommandWithInput() to provide custom stdin.
         * @param args Command arguments (e.g., "status", "-v")
         * @return Combined stdout and stderr
         */
        public String runCommand(String... args) throws Exception {
            return runCommandWithInput("n\n", args);
        }
        
        /**
         * Run a VGL command with custom stdin input.
         * @param input String to provide to stdin (e.g., "y\n" or "")
         * @param args Command arguments
         * @return Combined stdout and stderr
         */
        public String runCommandWithInput(String input, String... args) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream oldIn = System.in;
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            try {
                System.setIn(new java.io.ByteArrayInputStream(input.getBytes("UTF-8")));
                PrintStream ps = new PrintStream(baos, true, "UTF-8");
                System.setOut(ps);
                System.setErr(ps);
                // Construct the CLI in non-interactive mode to avoid prompts during
                // initialization (e.g., config loading). Then enable interactive
                // mode only for the actual command execution so provided stdin
                // (like "n\n") can be consumed by prompts the command emits.
                String prev = System.getProperty("vgl.noninteractive");
                try {
                    System.setProperty("vgl.noninteractive", "true");
                    VglCli cli = new VglCli();
                    // Enable interactive mode for the actual command execution and
                    // allow the harness to force interactive behavior even when
                    // tests run with a test base.
                    System.setProperty("vgl.noninteractive", "false");
                    System.setProperty("vgl.force.interactive", "true");
                    try {
                        cli.run(args);
                    } finally {
                        // Remove the transient force flag so we don't leak state
                        System.getProperties().remove("vgl.force.interactive");
                    }
                    // Diagnostic dump (opt-in): only enabled when the test JVM
                    // is started with `-Dvgl.debug.dump=true`. Do NOT auto-enable
                    // diagnostics for specific commands (commit/diff) because
                    // many tests assert exact command output.
                    if (Boolean.getBoolean("vgl.debug.dump")) {
                        try (org.eclipse.jgit.api.Git dbg = org.eclipse.jgit.api.Git.open(path.toFile())) {
                            java.io.PrintStream out = ps; // ps is the PrintStream we set for stdout/stderr above
                            out.println("DEBUG: dumping git state for repo: " + path.toString());
                            try {
                                org.eclipse.jgit.lib.ObjectId head = dbg.getRepository().resolve("HEAD");
                                out.println("DEBUG: HEAD -> " + head);
                                org.eclipse.jgit.lib.ObjectId headTree = dbg.getRepository().resolve("HEAD^{tree}");
                                out.println("DEBUG: HEAD^{tree} -> " + headTree);
                                if (headTree != null) {
                                    org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(dbg.getRepository());
                                    tw.addTree(headTree);
                                    tw.setRecursive(true);
                                    while (tw.next()) {
                                        out.println("DEBUG: tree-entry: " + tw.getPathString());
                                    }
                                    tw.close();
                                }
                            } catch (Exception e) {
                                out.println("DEBUG: could not resolve HEAD/tree: " + e.getMessage());
                            }
                            try {
                                org.eclipse.jgit.api.Status st = dbg.status().call();
                                out.println("DEBUG: status added=" + st.getAdded() + " modified=" + st.getModified() + " untracked=" + st.getUntracked());
                            } catch (Exception e) {
                                out.println("DEBUG: status failed: " + e.getMessage());
                            }
                        } catch (Exception e) {
                            ps.println("DEBUG: cannot open git repo for diagnostics: " + e.getMessage());
                        }
                    }
                } finally {
                    if (prev == null) System.getProperties().remove("vgl.noninteractive"); else System.setProperty("vgl.noninteractive", prev);
                }
                return baos.toString("UTF-8");
            } finally {
                System.setIn(oldIn);
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
        }
        
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
