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
     * Represents a test repository with automatic cleanup.
     */
    public static class VglTestRepo implements AutoCloseable {
        private final Path path;
        private final String originalUserDir;
        private final boolean hasGit;
        
        VglTestRepo(Path path, boolean initGit) throws Exception {
            this.path = path;
            this.originalUserDir = System.getProperty("user.dir");
            this.hasGit = initGit;
            
            // Create directory if needed
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            
            // Initialize git if requested
            if (initGit) {
                try (Git git = Git.init().setDirectory(path.toFile()).call()) {
                    // Configure git for tests
                    git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
                    git.getRepository().getConfig().setString("user", null, "name", "Test User");
                    git.getRepository().getConfig().save();
                }
            }
            
            // Set working directory
            System.setProperty("user.dir", path.toString());
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
                new VglCli().run(args);
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
                // Configure remote
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteUrl);
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
        }
    }
}
