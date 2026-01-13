package com.vgl.cli.utils;

public final class FormatUtils {
    private FormatUtils() {}

    public static String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    public static String truncateMiddle(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0 || value.length() <= maxLen) {
            return value;
        }
        if (maxLen <= 3) {
            return value.substring(0, maxLen);
        }

        int leftLen = (maxLen - 3) / 2;
        int rightLen = maxLen - 3 - leftLen;
        return value.substring(0, leftLen) + "..." + value.substring(value.length() - rightLen);
    }

    /**
     * Normalizes a remote URL/path for display only.
     *
     * <p>This avoids confusing output like showing a working-tree '.git' path or a '.git' URL suffix.
     * The underlying stored/used remote value is left unchanged.
     */
    public static String normalizeRemoteUrlForDisplay(String remoteUrl) {
        if (remoteUrl == null) {
            return null;
        }

        String value = remoteUrl.trim();
        if (value.isEmpty()) {
            return value;
        }

        // Trim trailing separators (common with URLs and paths)
        while (value.endsWith("/") || value.endsWith("\\")) {
            value = value.substring(0, value.length() - 1);
        }

        // If the remote ends with '.git' (URL suffix or local '.git' path), drop it for display.
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
            while (value.endsWith("/") || value.endsWith("\\")) {
                value = value.substring(0, value.length() - 1);
            }
        }

        return value;
    }
}
