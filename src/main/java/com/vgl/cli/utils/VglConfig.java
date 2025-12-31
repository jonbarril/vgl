package com.vgl.cli.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public final class VglConfig {
    private VglConfig() {}

    public static final String KEY_LOCAL_BRANCH = "local.branch";
    public static final String KEY_REMOTE_URL = "remote.url";
    public static final String KEY_REMOTE_BRANCH = "remote.branch";

    public static final String KEY_TRACKED_FILES = "tracked.files";
    public static final String KEY_UNTRACKED_FILES = "untracked.files";

    public static Properties readProps(Path repoRoot) {
        Properties props = new Properties();
        if (repoRoot == null) {
            return props;
        }
        Path vgl = repoRoot.resolve(".vgl");
        if (!Files.isRegularFile(vgl)) {
            return props;
        }
        try (var in = Files.newInputStream(vgl)) {
            props.load(in);
        } catch (Exception ignored) {
            // best-effort
        }
        return props;
    }

    public static void writeProps(Path repoRoot, Consumer<Properties> mutator) throws IOException {
        if (repoRoot == null) {
            throw new IOException("repoRoot is null");
        }
        Properties props = readProps(repoRoot);
        mutator.accept(props);
        Path vgl = repoRoot.resolve(".vgl");
        try (var out = Files.newOutputStream(vgl)) {
            props.store(out, "VGL state");
        }
    }

    public static Set<String> getPathSet(Properties props, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (props == null || key == null || key.isBlank()) {
            return out;
        }
        String val = props.getProperty(key, "");
        if (val == null || val.isBlank()) {
            return out;
        }
        String[] parts = val.split(",");
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            out.add(normalizeRepoRelativePath(trimmed));
        }
        return out;
    }

    public static void setPathSet(Properties props, String key, Collection<String> paths) {
        if (props == null || key == null || key.isBlank()) {
            return;
        }
        if (paths == null || paths.isEmpty()) {
            props.remove(key);
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            normalized.add(normalizeRepoRelativePath(p));
        }
        if (normalized.isEmpty()) {
            props.remove(key);
        } else {
            props.setProperty(key, String.join(",", normalized));
        }
    }

    public static String normalizeRepoRelativePath(String p) {
        if (p == null) {
            return null;
        }
        String n = p.trim().replace('\\', '/');
        while (n.startsWith("./")) {
            n = n.substring(2);
        }
        if (n.equals(".")) {
            return "";
        }
        // Avoid accidental absolute paths in config.
        if (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n;
    }

    public static void ensureGitignoreHasVgl(Path repoRoot) throws IOException {
        if (repoRoot == null) {
            return;
        }
        Path gitignore = repoRoot.resolve(".gitignore");
        String content = "";
        if (Files.isRegularFile(gitignore)) {
            content = Files.readString(gitignore, StandardCharsets.UTF_8);
        }

        if (!content.contains(".vgl")) {
            StringBuilder updated = new StringBuilder();
            if (!content.isBlank()) {
                updated.append(content);
                if (!content.endsWith("\n")) {
                    updated.append("\n");
                }
            }
            updated.append(".vgl\n");
            Files.writeString(gitignore, updated.toString(), StandardCharsets.UTF_8);
        }
    }
}
