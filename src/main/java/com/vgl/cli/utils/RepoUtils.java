package com.vgl.cli.utils;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RepoUtils {
    private RepoUtils() {}

    /**
     * Finds the nearest repository root by searching upward for either a .git directory or a .vgl file.
     * Honors the test ceiling system property (-Dvgl.test.base) if present.
     */
    public static Path findNearestRepoRoot(Path startDir) {
        if (startDir == null) {
            return null;
        }

        Path ceiling = null;
        String ceilingProp = System.getProperty("vgl.test.base");
        if (ceilingProp != null && !ceilingProp.isBlank()) {
            ceiling = Path.of(ceilingProp).toAbsolutePath().normalize();
        }

        Path current = startDir.toAbsolutePath().normalize();
        while (current != null) {
            if (isRepoRoot(current)) {
                return current;
            }

            if (ceiling != null && current.equals(ceiling)) {
                return null;
            }

            current = current.getParent();
        }

        return null;
    }

    public static boolean isRepoRoot(Path dir) {
        return Files.exists(dir.resolve(".git")) || Files.isRegularFile(dir.resolve(".vgl"));
    }

    public static boolean isNestedUnderExistingRepo(Path targetDir) {
        Path nearest = findNearestRepoRoot(targetDir);
        return nearest != null && !nearest.equals(targetDir.toAbsolutePath().normalize());
    }
}

