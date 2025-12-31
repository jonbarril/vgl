package com.vgl.cli.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utility for detecting ancestor (nested) Git/VGL repositories.
 */
public final class NestedRepoDetector {
    private NestedRepoDetector() {}

    /**
     * Finds the nearest ancestor directory (excluding the target itself) that contains a .git or .vgl repo.
     * Returns null if no ancestor repo is found.
     */
    public static Path findAncestorRepo(Path target) {
        Objects.requireNonNull(target, "target");
        Path search = target.getParent();
        while (search != null) {
            if (Files.exists(search.resolve(".git")) || Files.exists(search.resolve(".vgl"))) {
                return search;
            }
            search = search.getParent();
        }
        return null;
    }

    /**
     * Returns true if the target is nested under an ancestor repo (excluding itself).
     */
    public static boolean isNestedRepo(Path target) {
        return findAncestorRepo(target) != null;
    }
}
