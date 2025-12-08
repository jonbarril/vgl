package com.vgl.cli.commands;

import java.util.Set;
import java.util.List;

public class StatusFileSummary {
    public static void printFileSummary(int numModified, int numAdded, int numRemoved, int numReplaced,
                                       int numToMerge,
                                       Set<String> undecidedSet, Set<String> trackedSet,
                                       Set<String> untrackedSet, Set<String> ignoredSet) {
        // First line: compact counts of working-tree changes
        // Order and labels: Added, Modified, Renamed, Deleted
        String first = "FILES   " + numAdded + " Added, " + numModified + " Modified, " + numReplaced + " Renamed, " + numRemoved + " Deleted";
        System.out.println(first);

        // Second line: categorical summary aligned under the counts
        // Order: Undecided, Tracked, Untracked, Ignored
        List<String> fileSummary = new java.util.ArrayList<>();
        fileSummary.add(undecidedSet.size() + " Undecided");
        fileSummary.add(trackedSet.size() + " Tracked");
        fileSummary.add(untrackedSet.size() + " Untracked");
        fileSummary.add(ignoredSet.size() + " Ignored");

        // Align the second line with the start of the counts on the first line
        int labelLen = "FILES   ".length();
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < labelLen; i++) pad.append(' ');
        System.out.println(pad.toString() + String.join(", ", fileSummary));
    }

    // Backward-compatible overload: existing callers can continue to pass sets only.
    public static void printFileSummary(Set<String> undecidedSet, Set<String> trackedSet, Set<String> untrackedSet, Set<String> nestedRepos) {
        // Backward-compatible: assume no working-tree changes and no merges
        printFileSummary(0, 0, 0, 0, 0, undecidedSet, trackedSet, untrackedSet, nestedRepos);
    }
}
