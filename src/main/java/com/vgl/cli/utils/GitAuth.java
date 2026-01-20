package com.vgl.cli.utils;

import java.io.PrintStream;
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

    public static boolean isMissingCredentialsProvider(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (isMissingCredentialsProviderMessage(cur.getMessage())) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Prints a novice-friendly, consistent message for the common case where a remote requires auth,
     * but VGL has no credentials configured (and the user likely needs to sign in via browser).
     */
    public static boolean handleMissingCredentialsProvider(Throwable t, String remoteUrl, PrintStream err) {
        if (credentialsProviderFromEnvOrNull() != null) {
            return false;
        }
        if (!isMissingCredentialsProvider(t)) {
            return false;
        }
        if (remoteUrl == null || remoteUrl.isBlank() || err == null) {
            return false;
        }

        err.println("ERROR: Authentication required to access:");
        err.println("  " + remoteUrl);
        err.println("");
        err.println(authSetupHint(remoteUrl));
        return true;
    }

    /**
     * Heuristic for native git errors (no structured exception type): keeps behavior consistent with
     * JGit missing-credentials handling.
     */
    public static boolean handleNativeAuthFailure(Throwable t, String remoteUrl, PrintStream err) {
        if (remoteUrl == null || remoteUrl.isBlank() || err == null) {
            return false;
        }

        String message = null;
        Throwable cur = t;
        while (cur != null && (message == null || message.isBlank())) {
            message = cur.getMessage();
            cur = cur.getCause();
        }
        if (message == null) {
            message = "";
        }
        String m = message.toLowerCase();

        boolean authRelated = m.contains("authentication")
            || m.contains("not authorized")
            || m.contains("forbidden")
            || m.contains("permission denied")
            || m.contains("access denied")
            || m.contains("401")
            || m.contains("403");

        if (!authRelated) {
            return false;
        }

        err.println("ERROR: Authentication required to access:");
        err.println("  " + remoteUrl);
        err.println("");
        err.println(authSetupHint(remoteUrl));
        return true;
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
            "  1) Ensure your Git credential helper is set up (recommended: Git Credential Manager).",
            "     Note: signing into the website in a browser is not always enough by itself.",
            "  2) Run the command again so Git can authenticate.",
            "",
            "For automation (no prompts), you can set:",
            "  VGL_GIT_TOKEN (recommended) or VGL_GIT_PASSWORD",
            "  and optionally VGL_GIT_USERNAME."
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
