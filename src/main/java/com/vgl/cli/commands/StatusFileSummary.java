package com.vgl.cli.commands;

import java.util.Set;
import java.util.List;

public class StatusFileSummary {
    public static void printFileSummary(int numModified, int numAdded, int numRemoved, int numReplaced,
                                       Set<String> undecidedSet, Set<String> trackedSet,
                                       Set<String> untrackedSet, Set<String> nestedRepos) {
        // First line: compact counts of working-tree changes
        System.out.println("FILES   " + numModified + " Modified, " + numAdded + " Added, " + numRemoved + " Removed, " + numReplaced + " Replaced");

        // Second line: more user-friendly categorical summary (indented for clarity)
        List<String> fileSummary = new java.util.ArrayList<>();
        fileSummary.add(undecidedSet.size() + " Undecided");
        fileSummary.add(trackedSet.size() + " Tracked");
        fileSummary.add(untrackedSet.size() + " Untracked");
        fileSummary.add(nestedRepos.size() + " Ignored");
        // Second, indented categorical summary (no leading label)
        System.out.println("  " + String.join(", ", fileSummary));
    }

    // Backward-compatible overload: existing callers can continue to pass sets only.
    public static void printFileSummary(Set<String> undecidedSet, Set<String> trackedSet, Set<String> untrackedSet, Set<String> nestedRepos) {
        printFileSummary(0, 0, 0, 0, undecidedSet, trackedSet, untrackedSet, nestedRepos);
    }
}
