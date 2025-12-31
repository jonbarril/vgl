package com.vgl.cli.commands.helpers;

/**
 * Enum representing the canonical state of a file for status reporting.
 */
public enum FileStatusCategory {
    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    TRACKED,
    UNTRACKED,
    IGNORED,
    UNDECIDED
}
