package com.vgl.cli.utils;

import java.nio.file.Path;

import java.util.List;
import java.nio.file.Path;

public final class Messages {
    private Messages() {}

    public static final String ERR_NO_REPO_FOUND = "Error: No VGL repository found.";
    public static final String ERR_NO_REPO_AT_TARGET_PREFIX = "Error: No VGL or Git repository exists at target: ";
    public static final String WARN_MALFORMED_REPO_PREFIX = "Warning: Repository appears malformed or inconsistent at: ";
    public static final String ERR_REFUSING_CREATE_NESTED_REPO = "Refusing to create nested repository. Use -f to bypass.";
    public static final String ERR_REFUSING_DELETE_REPO = "Refusing to delete repository. Use -f to bypass.";

    public static final String OUT_CREATED_REPO_PREFIX = "Created VGL repository: ";
    public static final String OUT_DELETED_REPO_METADATA_PREFIX = "Deleted VGL repository metadata: ";
    public static final String OUT_DELETED_REPO_CONTENTS_PREFIX = "Deleted VGL repository directory: ";
    public static final String OUT_DELETED_BRANCH_PREFIX = "Deleted branch: ";
    public static final String OUT_SWITCHED_BRANCH_PREFIX = "Switched to branch: ";

    public static final String ERR_BRANCH_NOT_FOUND_PREFIX = "Error: Branch does not exist: ";
    public static final String WARN_DELETE_BRANCH_NOT_MERGED_PREFIX = "Warning: Branch is not merged into the current branch: ";
    public static final String WARN_DELETE_BRANCH_HAS_UNPUSHED_COMMITS_PREFIX = "Warning: Branch has unpushed commits: ";

    public static final String ERR_REPO_EXISTS_PREFIX = "Error: Repository already exists at: ";
    public static final String ERR_REFUSING_DELETE_CURRENT_BRANCH_PREFIX = "Error: Refusing to delete the current branch: ";

    public static final String WARN_REPO_DIRTY_OR_AHEAD = "Warning: Repository has uncommitted changes and/or unpushed commits.";

    public static final String WARN_NESTED_REPO_PREFIX = "Warning: Target directory is nested under an existing repository";

    public static final String ERR_UNKNOWN_COMMAND_PREFIX = "Unknown command: ";
    public static final String ERR_UNHANDLED_PREFIX = "ERROR: ";

    public static final String WARN_STATUS_NO_REPO_FOUND = "Warning: No VGL repository found. Run 'vgl create -lr <path>' to make one.";

    private static final String USAGE_TRACK = "Usage:\n  vgl track <glob...> | -all";
    private static final String USAGE_UNTRACK = "Usage:\n  vgl untrack <glob...> | -all";

    public static String nestedRepoPrompt(Path parentRepoRootOrNull) {
        String suffix = (parentRepoRootOrNull != null) ? " at: " + parentRepoRootOrNull : "";
        return WARN_NESTED_REPO_PREFIX + suffix + ". Continue? [y/N] ";
    }

    public static String createRepoRefusingNested() {
        return ERR_REFUSING_CREATE_NESTED_REPO;
    }

    public static String repoAlreadyExists(Path repoRoot) {
        return ERR_REPO_EXISTS_PREFIX + repoRoot;
    }

    public static String createdRepo(Path repoRoot, String branch) {
        return OUT_CREATED_REPO_PREFIX + repoRoot + " (branch: " + branch + ")";
    }

    public static String deleteRepoPrompt(Path repoRoot) {
        return "Delete repository support files (.git, .vgl, .gitignore) at " + repoRoot + "? [y/N] ";
    }

    public static String deleteRepoDirtyOrAheadPrompt() {
        return WARN_REPO_DIRTY_OR_AHEAD + " Continue? [y/N] ";
    }

    public static String deleteRepoContentsPrompt(Path repoRoot) {
        return "Delete repository contents too (delete directory) at " + repoRoot + "? [y/N] ";
    }

    public static String noRepoAtTarget(Path targetDir) {
        return ERR_NO_REPO_AT_TARGET_PREFIX + targetDir;
    }

    public static String malformedRepo(Path repoRoot, String problem) {
        if (problem == null || problem.isBlank()) {
            return WARN_MALFORMED_REPO_PREFIX + repoRoot;
        }
        return WARN_MALFORMED_REPO_PREFIX + repoRoot + ". " + problem;
    }

    public static String deleteRepoRefusing() {
        return ERR_REFUSING_DELETE_REPO;
    }

    public static String deletedRepoMetadata(Path repoRoot) {
        return OUT_DELETED_REPO_METADATA_PREFIX + repoRoot;
    }

    public static String deletedRepoContents(Path repoRoot) {
        return OUT_DELETED_REPO_CONTENTS_PREFIX + repoRoot;
    }

    public static String refusingDeleteCurrentBranch(String branch) {
        return ERR_REFUSING_DELETE_CURRENT_BRANCH_PREFIX + branch;
    }

    public static String deletedBranch(String branch) {
        return OUT_DELETED_BRANCH_PREFIX + branch;
    }

    public static String switchedBranch(String branch) {
        return OUT_SWITCHED_BRANCH_PREFIX + branch;
    }

    public static String branchNotFound(String branch) {
        return ERR_BRANCH_NOT_FOUND_PREFIX + branch;
    }

    public static String deleteBranchRiskPrompt(String branch) {
        return "Delete branch '" + branch + "' anyway? [y/N] ";
    }

    public static String unknownCommand(String command) {
        return ERR_UNKNOWN_COMMAND_PREFIX + command;
    }

    public static String unhandledError(String message) {
        return ERR_UNHANDLED_PREFIX + message;
    }

    public static String statusNoRepoFoundHint() {
        return WARN_STATUS_NO_REPO_FOUND;
    }

    public static String statusGitOnlyRepoHint(Path repoRoot) {
        return "Warning: Found Git repository without .vgl at: " + repoRoot + ". Re-run in interactive mode to convert.";
    }

    public static String statusVglOnlyRepoHint(Path repoRoot) {
        return "Warning: Found .vgl without .git at: " + repoRoot + ". Re-run in interactive mode to initialize Git.";
    }

    public static String statusConvertGitToVglPrompt(Path repoRoot) {
        return "Convert Git repository to VGL (create .vgl) at " + repoRoot + "? [y/N] ";
    }

    public static String statusInitGitFromVglPrompt(Path repoRoot) {
        return "Initialize Git repository from .vgl at " + repoRoot + "? [y/N] ";
    }

    public static String trackUsage() {
        return USAGE_TRACK;
    }

    public static String untrackUsage() {
        return USAGE_UNTRACK;
    }

    public static String trackNothingToDo() {
        return "No undecided files to track.";
    }

    public static String untrackNothingToDo() {
        return "No tracked files to untrack.";
    }

    public static String trackNoMatches() {
        return "Error: No matching files.";
    }

    public static String trackSuccess(List<String> files) {
        return "Tracking: " + String.join(" ", files);
    }

    public static String untrackSuccess(List<String> files) {
        return "Untracked: " + String.join(" ", files);
    }

    public static String trackAlreadyTracked(String file) {
        return "Error: File is already tracked: " + file;
    }

    public static String untrackNotTracked(String file) {
        return "Error: File is not tracked: " + file;
    }

    public static String trackIgnoringNested(List<String> paths) {
        return "Warning: Ignoring nested repository paths: " + String.join(" ", paths);
    }

    public static String untrackNestedError(List<String> paths) {
        return "Error: Cannot untrack nested repository paths: " + String.join(" ", paths);
    }

    public static String trackStageFailed(String file, String problem) {
        if (problem == null || problem.isBlank()) {
            return "Warning: Failed to add file to Git index: " + file;
        }
        return "Warning: Failed to add file to Git index: " + file + ". " + problem;
    }

    public static String untrackIndexFailed(String file, String problem) {
        if (problem == null || problem.isBlank()) {
            return "Warning: Failed to remove file from Git index: " + file;
        }
        return "Warning: Failed to remove file from Git index: " + file + ". " + problem;
    }

    public static String warnTargetRepoNotCurrent(Path targetRepoRoot) {
        String target = (targetRepoRoot != null) ? targetRepoRoot.toString() : "(unknown)";
        return "Warning: Target repo is not current; switch state unchanged. cd " + target;
    }
}
