package com.vgl.cli.commands.helpers;

import java.util.List;
import java.util.Set;

public final class StatusFileSummary {
    private StatusFileSummary() {}

    public static String getSummaryCountsLine(int numAdded, int numModified, int numRenamed, int numDeleted) {
        return numAdded + " Added, " + numModified + " Modified, " + numRenamed + " Renamed, " + numDeleted + " Deleted";
    }

    public static String getSummaryCategoriesLine(int undecided, int tracked, int untracked, int ignored) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add(undecided + " Undecided");
        parts.add(tracked + " Tracked");
        parts.add(untracked + " Untracked");
        parts.add(ignored + " Ignored");
        return String.join(", ", parts);
    }

    public static void printSummaryCountsLine(int numAdded, int numModified, int numRenamed, int numDeleted) {
        System.out.println(getSummaryCountsLine(numAdded, numModified, numRenamed, numDeleted));
    }

    public static void printSummaryCategoriesLine(Set<String> undecided, Set<String> tracked, Set<String> untracked, Set<String> ignored, int padLen) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < padLen; i++) {
            pad.append(' ');
        }
        System.out.println(pad + getSummaryCategoriesLine(undecided.size(), tracked.size(), untracked.size(), ignored.size()));
    }

    public static void printFileSummary(
        int numAdded,
        int numModified,
        int numRenamed,
        int numDeleted,
        Set<String> undecided,
        Set<String> tracked,
        Set<String> untracked,
        Set<String> ignored,
        int padLen
    ) {
        printSummaryCountsLine(numAdded, numModified, numRenamed, numDeleted);
        printSummaryCategoriesLine(undecided, tracked, untracked, ignored, padLen);
    }
}
