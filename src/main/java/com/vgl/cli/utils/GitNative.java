package com.vgl.cli.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GitNative {
    private GitNative() {
        // static only
    }

    public static boolean isGitAvailable() {
        try {
            ExecResult r = exec(List.of("git", "--version"), null);
            return r.exitCode == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Clone into an existing empty directory (workingDir). Uses `.` as the destination.
     */
    public static void cloneIntoCurrentDir(Path workingDir, String remoteUrl, String branch) throws IOException {
        if (workingDir == null) {
            throw new IllegalArgumentException("workingDir is null");
        }
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("remoteUrl is blank");
        }
        String b = (branch == null || branch.isBlank()) ? "main" : branch;

        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("clone");
        cmd.add("--quiet");
        cmd.add("--branch");
        cmd.add(b);
        cmd.add("--single-branch");
        cmd.add(remoteUrl);
        cmd.add(".");

        ExecResult r = exec(cmd, workingDir);
        if (r.exitCode != 0) {
            throw new IOException(failureMessage("git clone", r));
        }
    }

    /**
     * Returns the list of remote heads (branch names) using `git ls-remote --heads`.
     */
    public static List<String> listRemoteHeads(String remoteUrl) throws IOException {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("remoteUrl is blank");
        }

        List<String> cmd = List.of("git", "ls-remote", "--heads", remoteUrl);
        ExecResult r = exec(cmd, null);
        if (r.exitCode != 0) {
            throw new IOException(failureMessage("git ls-remote", r));
        }

        List<String> out = new ArrayList<>();
        String[] lines = r.stdout.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            // Format: <sha>\trefs/heads/<name>
            int tab = line.indexOf('\t');
            if (tab < 0 || tab + 1 >= line.length()) {
                continue;
            }
            String ref = line.substring(tab + 1).trim();
            String prefix = "refs/heads/";
            if (ref.startsWith(prefix) && ref.length() > prefix.length()) {
                out.add(ref.substring(prefix.length()));
            }
        }
        Collections.sort(out);
        return out;
    }

    private static String failureMessage(String label, ExecResult r) {
        String stderr = (r.stderr == null) ? "" : r.stderr.trim();
        String stdout = (r.stdout == null) ? "" : r.stdout.trim();
        String details = !stderr.isBlank() ? stderr : stdout;
        if (details.isBlank()) {
            details = "(no output)";
        }
        return label + " failed (exit " + r.exitCode + "): " + details;
    }

    private static ExecResult exec(List<String> command, Path workingDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        // Use environment as-is so Git can access credential helpers.
        Process p = pb.start();
        String stdout;
        String stderr;
        try (InputStream out = p.getInputStream(); InputStream err = p.getErrorStream()) {
            stdout = readAll(out);
            stderr = readAll(err);
        }
        int exit;
        try {
            exit = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running git", e);
        }
        return new ExecResult(exit, stdout, stderr);
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) {
            bos.write(buf, 0, read);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    private record ExecResult(int exitCode, String stdout, String stderr) {}
}
