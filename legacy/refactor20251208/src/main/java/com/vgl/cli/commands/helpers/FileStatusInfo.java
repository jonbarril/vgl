package com.vgl.cli.commands.helpers;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the state of a file for robust status reporting.
 */
public class FileStatusInfo {
    public final String path;
    public final EnumSet<FileStatusCategory> categories;

    public FileStatusInfo(String path, EnumSet<FileStatusCategory> categories) {
        this.path = path;
        this.categories = categories;
    }

    public boolean is(FileStatusCategory cat) {
        return categories.contains(cat);
    }

    public static Map<String, FileStatusInfo> buildFileStatusMap(
            Set<String> added,
            Set<String> modified,
            Set<String> removed,
            Set<String> renamed,
            Set<String> tracked,
            Set<String> untracked,
            Set<String> ignored,
            Set<String> undecided) {
        Map<String, FileStatusInfo> map = new LinkedHashMap<>();
        for (String f : added) map.put(f, new FileStatusInfo(f, EnumSet.of(FileStatusCategory.ADDED)));
        for (String f : modified) map.put(f, new FileStatusInfo(f, EnumSet.of(FileStatusCategory.MODIFIED)));
        for (String f : removed) map.put(f, new FileStatusInfo(f, EnumSet.of(FileStatusCategory.REMOVED)));
        for (String f : renamed) map.put(f, new FileStatusInfo(f, EnumSet.of(FileStatusCategory.RENAMED)));
        for (String f : tracked) map.computeIfAbsent(f, k -> new FileStatusInfo(f, EnumSet.noneOf(FileStatusCategory.class))).categories.add(FileStatusCategory.TRACKED);
        for (String f : untracked) map.computeIfAbsent(f, k -> new FileStatusInfo(f, EnumSet.noneOf(FileStatusCategory.class))).categories.add(FileStatusCategory.UNTRACKED);
        for (String f : ignored) map.computeIfAbsent(f, k -> new FileStatusInfo(f, EnumSet.noneOf(FileStatusCategory.class))).categories.add(FileStatusCategory.IGNORED);
        for (String f : undecided) map.computeIfAbsent(f, k -> new FileStatusInfo(f, EnumSet.noneOf(FileStatusCategory.class))).categories.add(FileStatusCategory.UNDECIDED);
        return map;
    }
}
