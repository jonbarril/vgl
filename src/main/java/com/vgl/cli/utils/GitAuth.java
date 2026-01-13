package com.vgl.cli.utils;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public final class GitAuth {
    private GitAuth() {
        // static only
    }

    public static CredentialsProvider credentialsProviderFromEnvOrNull() {
        String username = getenvNonBlank("VGL_GIT_USERNAME");
        String token = getenvNonBlank("VGL_GIT_TOKEN");
        String password = getenvNonBlank("VGL_GIT_PASSWORD");

        String secret = (token != null) ? token : password;
        if (secret == null) {
            return null;
        }

        if (username == null) {
            // For PAT-based auth (e.g. GitHub/GitLab), the username is often ignored.
            username = "token";
        }

        return new UsernamePasswordCredentialsProvider(username, secret);
    }

    public static <C extends TransportCommand<C, ?>> C applyCredentialsIfPresent(C command) {
        if (command == null) {
            return null;
        }
        CredentialsProvider provider = credentialsProviderFromEnvOrNull();
        if (provider != null) {
            command.setCredentialsProvider(provider);
        }
        return command;
    }

    public static boolean isMissingCredentialsProviderMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        // JGit transport error message when credentials are required but none are configured.
        return message.contains("Authentication is required but no CredentialsProvider has been registered");
    }

    public static String authEnvHint() {
        return String.join("\n",
            "Hint: Set VGL_GIT_USERNAME + VGL_GIT_TOKEN (or VGL_GIT_PASSWORD) and retry.",
            "      For GitHub, use a Personal Access Token (PAT) as VGL_GIT_TOKEN.",
            "      Or use an SSH URL (git@host:org/repo.git) with SSH keys configured."
        );
    }

    private static String getenvNonBlank(String name) {
        try {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
