# VGL: Use Case Bones – Legacy, Alternate, and Future Concepts

## Concept Overview

The original advanced concept for VGL was to decouple the current working directory (cwd) from the current repository context. This enables workflows where the user’s shell location and the repository context for VGL commands are independent, allowing for powerful multi-repo and cross-repo operations.

### Key Features
- **Decoupled State:** VGL maintains a user-level state file (e.g., in $HOME/.vgl or %USERPROFILE%\.vgl) that tracks:
  - The current repo context (repo root + branch)
  - The jump/alternate state (previous context)
- **Context Switching:**
  - `switch -lr <path>` sets the current context to the repo resolved from `<path>` (or cwd if `.`), and updates the jump state to the previous context.
  - `jump` swaps the current and jump states, allowing fast toggling between two repos/branches.
- **Command Behavior:**
  - All VGL commands operate relative to the current repo context, regardless of the user’s cwd.
  - File arguments and globs are resolved relative to the user’s actual cwd.

### Example Workflow
1. User is in `repoA` directory:
   ```
   cd /work/repoA
   vgl switch -lr .   # Sets context to repoA (main)
   ```
2. User switches to `repoB` context:
   ```
   vgl switch -lr /work/repoB   # Context is now repoB (main), jump state is repoA (main)
   ```
3. User runs VGL commands (e.g., split, merge) in the context of repoB, but file paths are still relative to cwd (could be in repoA or elsewhere).
4. User runs `vgl jump` to toggle back to repoA context (and cwd remains unchanged).

### Benefits
- Enables advanced multi-repo workflows, cross-repo operations, and persistent context switching.
- Power users can script or automate complex operations without changing directories.
- Jump/switch become true context toggles, not just navigation aids.

### Tradeoffs
- More complex for new users to understand (context and cwd can differ).
- Requires robust CLI output to always show current context and cwd.
- Needs careful state management and error handling.

### Summary
This model is powerful for advanced users and automation, but adds complexity. It is best suited for environments where multi-repo workflows are common and users are comfortable with explicit context management. For most users, a simpler cwd-based model is recommended, but this alternate design remains a valuable reference for future enhancements or power-user features.
