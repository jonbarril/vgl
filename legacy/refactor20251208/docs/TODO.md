# VGL TODO List

## Test Infrastructure Refactoring

### VglTestHarness Naming Clarity
**Priority**: Low  
**Status**: Not Started

The current `createRepo()` and `createDir()` naming is misleading:
- `createRepo()` only creates `.git` (git repo), not `.vgl` (vgl repo)
- Tests needing vgl features must call `createRepo()` then `create -lr` command

**Proposed Refactor**:
```java
// Current (misleading):
createRepo(tmp)    // Creates .git only
createDir(tmp)     // Creates directory only

// Better naming:
createGitRepo(tmp)     // Creates .git only (for track, basic git ops)
createVglRepo(tmp)     // Creates .git + .vgl (for commit, diff, full vgl ops)
createDir(tmp)         // Creates directory only (for testing create command)
```

**Impact**: 
- All existing tests using `createRepo()` would need updates
- Tests calling `create -lr` after `createRepo()` would simplify
- Clearer test intent and less confusion

**Related Files**:
- `src/test/java/com/vgl/cli/VglTestHarness.java`
- All test files using createRepo() (~20+ tests)
