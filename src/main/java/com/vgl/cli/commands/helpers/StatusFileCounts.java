package com.vgl.cli.commands.helpers;

import org.eclipse.jgit.api.Status;

public final class StatusFileCounts {
    public final int modified;
    public final int added;
    public final int removed;
    public final int replaced;

    private StatusFileCounts(int modified, int added, int removed, int replaced) {
        this.modified = modified;
        this.added = added;
        this.removed = removed;
        this.replaced = replaced;
    }

    public static StatusFileCounts fromStatus(Status status) {
        if (status == null) return new StatusFileCounts(0,0,0,0);
        int numModified = status.getChanged().size() + status.getModified().size();
        int numAdded = status.getAdded().size();
        int numRemoved = status.getRemoved().size() + status.getMissing().size();
        int numReplaced = 0; // JGit Status does not expose replaced directly
        return new StatusFileCounts(numModified, numAdded, numRemoved, numReplaced);
    }
}
