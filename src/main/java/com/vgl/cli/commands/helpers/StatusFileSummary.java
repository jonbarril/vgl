package com.vgl.cli.commands.helpers;

import java.util.Set;
import java.util.List;

public class StatusFileSummary {
    public static void printFileSummary(int numModified, int numAdded, int numRemoved, int numReplaced,
                                       int numToMerge,
                                       Set<String> undecidedSet, Set<String> trackedSet,
                                       Set<String> untrackedSet, Set<String> ignoredSet,
                                       int padLen) {
        // First line: compact counts of working-tree changes
        // Order and labels: Added, Modified, Renamed, Deleted
        String first = getSummaryCountsLine(numAdded, numModified, numReplaced, numRemoved);
        System.out.println(first);

        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < padLen; i++) pad.append(' ');
        String second = pad.toString() + getSummaryCategoriesLine(undecidedSet.size(), trackedSet.size(), untrackedSet.size(), ignoredSet.size());
        System.out.println(second);
    }

    public static String getSummaryCountsLine(int numAdded, int numModified, int numReplaced, int numRemoved) {
        return numAdded + " Added, " + numModified + " Modified, " + numReplaced + " Renamed, " + numRemoved + " Deleted";
    }

    public static String getSummaryCategoriesLine(int undecided, int tracked, int untracked, int ignored) {
        List<String> fileSummary = new java.util.ArrayList<>();
        fileSummary.add(undecided + " Undecided");
        fileSummary.add(tracked + " Tracked");
        fileSummary.add(untracked + " Untracked");
        fileSummary.add(ignored + " Ignored");
        return String.join(", ", fileSummary);
    }

    // Backward-compatible overload: existing callers can continue to pass sets only.
    public static void printFileSummary(Set<String> undecidedSet, Set<String> trackedSet, Set<String> untrackedSet, Set<String> ignoredSet) {
        // Backward-compatible: assume no working-tree changes and no merges
        printFileSummary(0, 0, 0, 0, 0, undecidedSet, trackedSet, untrackedSet, ignoredSet, 8);
    }
}
