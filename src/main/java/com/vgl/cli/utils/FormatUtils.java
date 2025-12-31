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
}
