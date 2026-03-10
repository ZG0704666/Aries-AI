# Compose Migration Progress

Last updated: 2026-03-10
Branch: `codex/refactor-md3-modular`

## Overall

- Visible UI migration progress: `~60%`
- Full architectural migration progress: `~40%`

## Completed

- Shared Compose Material 3 theme scaffold is in place.
- Settings page has migrated to Compose.
- About page has migrated to Compose.
- Automation control panel visible UI has migrated to Compose.
- Main top bar has migrated to Compose.
- Main drawer has migrated to Compose.
- Main input area has migrated to Compose.
- Main transcript visible layer has migrated to Compose.

## In Progress

- Automation page still uses hidden legacy Views as a bridge for business logic and state sync.
- Main transcript still relies on hidden legacy rendering for some runtime behavior.
- MainActivity still contains mixed View + Compose state and compatibility code.

## Remaining

- Replace main chat streaming render path with pure Compose rendering.
- Replace AI message actions in the main transcript with Compose interactions.
- Replace automation card interactions in the main transcript with Compose interactions.
- Remove hidden legacy message container dependency from `MainActivity`.
- Remove hidden legacy automation container dependency from `AutomationActivityNew`.
- Move more screen state to Compose-first state holders instead of View-driven mutation.

## Current Workstreams

### 1. Main chat

- Compose transcript host is wired in.
- Need to finish streaming, retry, copy, and automation message interactions.

### 2. Automation

- Compose shell is active.
- Need to remove the hidden legacy bridge and migrate runtime controls fully.

### 3. Cleanup

- Remove old XML/View compatibility code after Compose paths become feature-complete.
- Continue shrinking `MainActivity.kt` into smaller Compose-oriented modules.

## Next Milestone

- Main chat fully Compose-driven for:
  - transcript rendering
  - streaming updates
  - retry/copy actions
  - automation card actions

After that, the project should be close to `75%+` on visible + interaction migration.
