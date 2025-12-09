package com.vgl.refactor;

import java.util.LinkedHashSet;
import java.util.Set;

public final class StatusModel {
    public int modified;
    public int added;
    public int removed;
    public int renamed;

    public final Set<String> tracked = new LinkedHashSet<>();
    public final Set<String> untracked = new LinkedHashSet<>();
    public final Set<String> undecided = new LinkedHashSet<>();
    public final Set<String> ignored = new LinkedHashSet<>();
    public final Set<String> renameTargets = new LinkedHashSet<>();
    public final Set<String> renameSources = new LinkedHashSet<>();

    public StatusModel() {}
}
