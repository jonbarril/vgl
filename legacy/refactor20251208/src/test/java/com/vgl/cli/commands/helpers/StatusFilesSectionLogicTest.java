package com.vgl.cli.commands.helpers;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class StatusFilesSectionLogicTest {
    @Test
    public void testFilesToCommitRobustSets() {
        // Simulate filesToCommit map as used in StatusFilesSection
        Map<String, String> filesToCommit = new LinkedHashMap<>();
        filesToCommit.put("A.txt", "A");
        filesToCommit.put("M.txt", "M");
        filesToCommit.put("D.txt", "D");
        filesToCommit.put("R.txt", "R");

        Set<String> added = new LinkedHashSet<>();
        Set<String> modified = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        Set<String> renamed = new LinkedHashSet<>();

        for (Map.Entry<String, ?> e : filesToCommit.entrySet()) {
            Object v = e.getValue();
            String key = e.getKey();
            if (v != null) {
                String s = v.toString();
                if ("A".equals(s)) added.add(key);
                else if ("M".equals(s)) modified.add(key);
                else if ("D".equals(s)) removed.add(key);
                else if ("R".equals(s)) renamed.add(key);
            }
        }

        assertEquals(Set.of("A.txt"), added, "Added set should contain only A.txt");
        assertEquals(Set.of("M.txt"), modified, "Modified set should contain only M.txt");
        assertEquals(Set.of("D.txt"), removed, "Removed set should contain only D.txt");
        assertEquals(Set.of("R.txt"), renamed, "Renamed set should contain only R.txt");
    }
}
