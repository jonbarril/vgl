package com.vgl.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class VglStateStore {
    private VglStateStore() {}

    public static class VglState {
        public String localDir;
        public String localBranch;
        public String remoteUrl;
        public String remoteBranch;
        public String updatedAt;
    }

    public static Path getDefaultStatePath() {
        String override = System.getProperty("vgl.state");
        if (override != null && !override.isBlank()) return Paths.get(override);
        String home = System.getProperty("user.home");
        return Paths.get(home, ".vgl", "state.properties");
    }

    public static VglState read() {
        Path p = getDefaultStatePath();
        if (!Files.exists(p)) return null;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(p)) {
            props.load(in);
            VglState s = new VglState();
            s.localDir = props.getProperty("localDir");
            s.localBranch = props.getProperty("localBranch");
            s.remoteUrl = props.getProperty("remoteUrl");
            s.remoteBranch = props.getProperty("remoteBranch");
            s.updatedAt = props.getProperty("updatedAt");
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    public static void write(VglState s) {
        if (s == null) return;
        Path p = getDefaultStatePath();
        try {
            Files.createDirectories(p.getParent());
            Properties props = new Properties();
            if (s.localDir != null) props.setProperty("localDir", s.localDir);
            if (s.localBranch != null) props.setProperty("localBranch", s.localBranch);
            if (s.remoteUrl != null) props.setProperty("remoteUrl", s.remoteUrl);
            if (s.remoteBranch != null) props.setProperty("remoteBranch", s.remoteBranch);
            if (s.updatedAt != null) props.setProperty("updatedAt", s.updatedAt);
            try (OutputStream out = Files.newOutputStream(p)) {
                props.store(out, "VGL state (do not edit manually unless you know what you're doing)");
            }
        } catch (IOException e) {
            // ignore write failures silently; state is advisory
        }
    }
}
