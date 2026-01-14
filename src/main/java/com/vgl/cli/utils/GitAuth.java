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
        // Back-compat method name: keep it, but make the content novice-friendly.
        return authSetupHint(null);
    }

    public static String authSetupHint(String remoteUrl) {
        return String.join("\n",
            "This repository requires you to be signed in.",
            "",
            "To continue:",
            "  1) Open your browser and sign in to the repository host",
            "     (the site where this repo lives).",
            "  2) Come back here and run the same command again.",
            "",
            "If you are already signed in, retry the command."
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
