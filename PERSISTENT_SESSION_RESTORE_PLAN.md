# Persistent Session Restore Plan

## Purpose

Miniverse currently persists enough vanilla data to restore worlds, positions, and sometimes player inventories, but it does not consistently restore the full match runtime. This document records the current issues, the architectural reason they happen, and a proposed plan to make retained session relaunch behave like a true "continue where we left off" flow.

The goal is not to patch individual gamemodes with guards. The goal is to make session restoration a first-class lifecycle path that all gamemodes use consistently.

## Current Situation

Minecraft/Fabric naturally persists vanilla server state under the backend `world/` folder:

- Blocks and chunks.
- Entities.
- Playerdata.
- Player position, dimension, health, hunger, inventory, XP, and effects.

Miniverse adds runtime state that vanilla does not know about:

- Active `Minigame` Java object.
- `MinigameRuntime` state.
- `MinigameContext` participant set.
- Gamemode teams, roles, scores, objectives, timers, cooldowns, target assignments, respawn queues, and tracking data.
- `MatchLifecycleController` phase and countdowns.
- Freeze state.
- Disconnect grace state.
- Main-server session registry lifecycle flags such as `stopRequested`, `returnComplete`, `seedChangeRequested`, and `pauseRequested`.

Those runtime objects disappear when the backend JVM stops. They must be explicitly serialized and restored.

## Observed Bugs

### Manhunt

What works:

- Backend world restores.
- Player inventories restore.
- Player positions restore.
- Some gamemode runtime state restores through `PersistentMinigame`.

What fails:

- Restored backend starts returning players to the lobby.

Likely cause:

- The retained session relaunch reused or re-observed stale registry handoff flags, especially `stopRequested` / `returnComplete`.
- The backend interpreted the session as stopped and entered return routing even though the match itself had not ended.

Required fix:

- Retained relaunch must clear transient stop/return/seed-change handoff flags before backend config is written and before backend startup observes the registry.
- Return routing must only run for an intentional admin stop, match-end return, or seed-change handoff, not for a retained relaunch.

### Bounty Hunt

What works:

- Backend world restores.
- Generic `playerStates` may exist in the save.

What fails:

- Runtime state is reconstructed as a new match.
- Inventories may be lost because match-start freeze/inventory-clear can replay before player snapshot restore.
- Target assignment, score, grace period, trackers, and countdown state are not consistently restored.

Likely cause:

- `BountyHuntMinigame` did not implement `PersistentMinigame`.
- The bootstrap path treated retained relaunch like a new session startup.

Required fix:

- Persist Bounty Hunt runtime state.
- Restore runtime before player join/start lifecycle logic.
- Treat restored active/start-phase saves as resumable, not as new matches.

### Death Swap

What works:

- Backend world restores.

What fails:

- Runtime state is reconstructed as a new match.
- Inventories may be missing if `playerStates` were not captured or were captured before participants were known.
- Swap timer, alive set, points, pending assignment, recent targets, and attribution state are lost.

Likely cause:

- `DeathSwapMinigame` did not implement `PersistentMinigame`.
- Generic player snapshot capture depends on the runtime participant set being correct at save time.

Required fix:

- Persist Death Swap runtime state.
- Ensure participant identity is loaded before generic player-state restore and before autosave/shutdown save.

### Speedrun

Current status:

- Does not implement `PersistentMinigame`.

Likely restore issues:

- Runner identity can be reconstructed from join order instead of persisted authority.
- Elapsed timer resets.
- Runtime state and scoreboard are rebuilt as if fresh.
- Generic player state may restore inventory/position, but match progression is not reliable.

Required fix:

- Persist runner UUID, participants, elapsed ticks, tick counter, state, and any future speedrun-specific flags.

### Resource Sprint

Current status:

- Does not implement `PersistentMinigame`.

Likely restore issues:

- Active objective list is lost, especially for probabilistic objective distribution.
- Team progress is lost.
- Objective scores and completion ticks are lost.
- Sudden death state is lost.
- Time warnings, elapsed timer, momentum, and statistics are lost.
- Match may resume with world/player inventory but not the actual race state.

Required fix:

- Persist settings-derived active objectives, team assignments, team progress, elapsed ticks, sudden death data, score maps, completion ticks, momentum/statistics if they affect behavior or post-game reporting.

## Why Vanilla-Style Restart Is Not Enough

A normal vanilla server continues after restart because the server's authoritative state is mostly stored in world/playerdata files.

Miniverse's authoritative match state is split across:

- Retained backend world folder.
- `miniverse-game-session.json`.
- `session.json` in the main-server registry.
- Runtime Java objects.
- Gamemode-specific in-memory fields.
- Lifecycle controller in-memory fields.
- Client transition/readiness state.

Restarting the JVM recreates only vanilla state. It cannot recreate Java fields unless Miniverse writes and reads them.

Therefore, a retained relaunch must not be "start a backend and hope vanilla state is enough." It must be "restore a saved Miniverse session."

## Desired Restore Invariants

These should be treated as framework rules:

- Retained relaunch must boot the retained backend folder in place, not copy only the world.
- Retained relaunch must clear transient lifecycle handoff flags before backend startup.
- Backend restore must load session save data before normal match-start logic can run.
- Runtime state must be restored before players are treated as new-match joiners.
- Generic player snapshots must be restored after the player entity exists but before match-start inventory clearing can happen.
- Active, paused, starting, and frozen saved states must be resumable states.
- New-match bootstrap and retained-session restore must be separate lifecycle branches.
- `MatchLifecycleController` must not enter start freeze or return countdown unless the restored lifecycle state says it should.
- Gamemode runtime JSON must not contain live Minecraft references.
- All long-lived identity must be UUID-based.
- A gamemode that wants retained-session support must implement runtime persistence.

## Proposed Architecture

### 1. Introduce a Restore Classification Step

Before bootstrap begins, classify the backend launch:

- `NEW_SESSION`: no retained runtime save exists.
- `RESTORE_SESSION`: compatible `miniverse-game-session.json` exists.
- `INSPECTION_SESSION`: spectator-safe copied world inspection.

This should be derived from backend config and files, not guessed from gamemode state.

Possible API:

```java
enum BackendLaunchMode {
    NEW_SESSION,
    RESTORE_SESSION,
    INSPECTION_SESSION
}
```

Session config should explicitly write whether the backend is a retained restore. File existence can remain a fallback.

### 2. Create a Restore Coordinator

Add a framework-level component responsible for restore ordering.

Possible name:

- `SessionRestoreCoordinator`
- `BackendSessionRestoreService`
- `PersistentSessionBootstrap`

Responsibilities:

- Read `miniverse-game-session.json`.
- Validate version/game/session id.
- Normalize legacy or inconsistent state where safe.
- Restore `MinigameRuntime` state.
- Restore gamemode payload if `PersistentMinigame`.
- Restore participant UUID roster.
- Expose per-player snapshots for join-time restore.
- Restore lifecycle phase when lifecycle persistence is added.
- Report diagnostics.

This avoids scattering restore decisions across `SessionBootstrapper`, `MinigameSessionStore`, and gamemodes.

### 3. Split New-Match Bootstrap From Restore Bootstrap

Current bootstrap has one path:

1. Player joins.
2. Runtime is created.
3. Settings are applied.
4. Participants are added.
5. Match intro is sent.
6. Clients become ready.
7. `beginMatch` starts freeze.
8. `startGame()` runs.

Restore needs a different path:

1. Backend starts.
2. Runtime is created.
3. Settings are applied.
4. Saved runtime state is loaded.
5. Saved participant UUIDs are attached.
6. Lifecycle state is restored or marked already active.
7. Players join.
8. Player snapshots are restored.
9. Transition overlay is released.
10. Match continues without `beginMatch()` or `startGame()` replay.

The critical rule: restore must not call normal start logic unless the saved match was never started.

### 4. Persist Lifecycle State Separately

Gamemodes should not own generic lifecycle data.

Persist a framework lifecycle block:

```json
"lifecycle": {
  "phase": "RUNNING",
  "gameState": "IN_PROGRESS",
  "ticksRemaining": 0,
  "lastAnnouncedSecond": -1,
  "startOverlayReleased": true,
  "returnPending": false,
  "disconnectGrace": {
    "ticksRemaining": 0,
    "players": []
  }
}
```

This allows restore to distinguish:

- Match running normally.
- Match paused.
- Match still in start freeze.
- Match in end return countdown.
- Match ended but return was cancelled.

Open design decision:

- For `START_FREEZE`, either resume the remaining countdown or normalize to `RUNNING`.
- Recommended: resume the remaining countdown only if lifecycle was explicitly persisted; otherwise normalize to `RUNNING` to avoid accidental inventory clearing.

### 5. Make Player State Store Roster-Based

Current generic player snapshot capture depends on live participants. This fails if participant state was cleared before shutdown/disconnect save.

Improve capture strategy:

- Use runtime participant UUIDs as the authoritative roster.
- Resolve live players from server when available.
- Capture only live player snapshots, but keep previous snapshots for offline participants instead of dropping them.
- On save, merge current live snapshots into previous `playerStates`.

This prevents disconnect/shutdown saves with empty `playerStates` from destroying the last good inventory snapshot.

Proposed behavior:

- If a participant is online, capture fresh snapshot.
- If a participant is offline but previous snapshot exists, keep previous snapshot.
- If a player is not in the participant roster, do not save as participant state unless admitted by a dynamic join flow.

### 6. Require Persistent Runtime Coverage Per Gamemode

All gamemodes intended to support retained restore should implement `PersistentMinigame`.

Minimum persisted fields by mode:

Speedrun:

- State.
- Runner UUID.
- Participant UUIDs.
- Elapsed ticks.
- Tick counter.

Resource Sprint:

- State.
- Settings or settings hash/version.
- Participant UUIDs.
- Team assignments.
- Active objectives after distribution.
- Team progress current objective index.
- Last objective claimant.
- Objective scores.
- Last completion ticks.
- Elapsed ticks.
- Sudden death active flag.
- Sudden death teams.
- Time warnings shown.
- Momentum/statistics if they affect gameplay or final report.

Manhunt:

- Already partially implemented.
- Review for stale terminal state, alive runner repair behavior, and whether `WAITING_FOR_PLAYERS` normalization is too broad.

Bounty Hunt:

- Persist target assignments, scores, invincibility windows, tracker cooldowns, tracking positions, grace timer, swap timer, participants, and settings.

Death Swap:

- Persist alive participants, points, swap timer, swap count, pending assignment, recent targets, death attributions, participants, settings, and state.

### 7. Separate Transient Registry Handoff From Persistent Session State

`session.json` currently stores lifecycle flags used for cross-process handoff:

- `stopRequested`
- `returnComplete`
- `seedChangeRequested`
- `pauseRequested`

These are not all persistent match state. Some are one-shot commands.

Recommended model:

- `commands`: transient handoff requests consumed by backend.
- `status`: durable session status for main-server UI.
- `recovery`: diagnostics.

Example:

```json
"commands": {
  "stopRequested": false,
  "seedChangeRequested": false,
  "pauseRequested": false
},
"status": {
  "returnComplete": false,
  "lastKnownBackendState": "RUNNING"
}
```

Short-term fix:

- Continue using existing flags, but clear stop/return/seed-change before retained relaunch writes backend config.

Long-term fix:

- Give transient commands request ids or timestamps so stale requests cannot affect a new backend generation.

### 8. Add Restore Diagnostics

Every retained restore should log and optionally write diagnostics:

- Save path used.
- Save version.
- Game type.
- Saved game state.
- Saved lifecycle phase.
- Participant count.
- Player snapshot count.
- Whether gamemode runtime payload was loaded.
- Whether lifecycle was restored or normalized.
- Whether any stale registry flags were cleared.
- Any player snapshots missing at join time.

This makes future bugs visible without reading large logs manually.

## Implementation Options

### Option A: Minimal Incremental Fix

Scope:

- Keep current architecture.
- Add `PersistentMinigame` to Speedrun and Resource Sprint.
- Keep generic restore changes in `MinigameSessionStore` and `SessionBootstrapper`.
- Improve `PlayerStateStore` merge behavior.
- Clear lifecycle flags on retained relaunch.

Pros:

- Fastest.
- Least risky.
- Fixes current gamemodes.

Cons:

- Restore logic remains spread across multiple classes.
- Lifecycle controller state is still not fully persisted.
- Future gamemodes can repeat mistakes.

### Option B: Framework Restore Coordinator

Scope:

- Add `SessionRestoreCoordinator`.
- Make `SessionBootstrapper` delegate restore decisions to it.
- Add explicit backend launch mode to session config.
- Implement `PersistentMinigame` for all current gamemodes.
- Add player snapshot merge behavior.

Pros:

- Cleaner architecture.
- Centralizes restore ordering.
- Easier future gamemode support.

Cons:

- Medium refactor.
- Needs careful testing around new sessions, restore sessions, late joins, reconnects, and inspections.

### Option C: Full Persistent Lifecycle Model

Scope:

- Everything in Option B.
- Persist and restore `MatchLifecycleController` state.
- Separate transient registry commands from durable status.
- Add save schema migration and validation.
- Add restore diagnostics UI/admin command.

Pros:

- Most correct long-term model.
- Handles start-freeze, pause, return countdown, and disconnect grace consistently.
- Makes retained restore truly first-class.

Cons:

- Largest change.
- Requires more design/testing.
- More moving parts around cross-process flags.

## Recommended Path

Use a staged version of Option B, then selectively add Option C lifecycle persistence.

### Stage 1: Stabilize Restore Semantics

- Keep retained backend folder boot-in-place.
- Ensure stale lifecycle handoff flags are cleared before relaunch.
- Add explicit backend launch mode to `miniverse-session.json`.
- Make `SessionBootstrapper` branch clearly between new session and restore session.
- Add restore diagnostics logging.

Acceptance criteria:

- Restored active sessions do not enter start countdown.
- Restored active sessions do not enter return countdown unless save says they were returning.
- Players joining restored sessions get their snapshots restored.

### Stage 2: Complete Runtime Persistence Coverage

- Implement `PersistentMinigame` for Speedrun.
- Implement `PersistentMinigame` for Resource Sprint.
- Review and harden Manhunt runtime restore.
- Keep Bounty Hunt and Death Swap persistence aligned with the same schema style.

Acceptance criteria:

- Every current gamemode can save and reload active match runtime state.
- Runtime save JSON has participant ids and a runtime payload.
- Restart/relaunch does not depend on gamemode-specific join order.

### Stage 3: Harden Player Snapshot Persistence

- Merge previous offline player snapshots into new saves.
- Do not overwrite a useful snapshot with an empty `playerStates` array unless the session truly has no participants.
- Add warnings when participant snapshots are missing.

Acceptance criteria:

- Shutdown after disconnect does not erase the last good inventory snapshot.
- DeathSwap-like empty player-state saves no longer happen for active participant rosters.

### Stage 4: Persist Lifecycle Controller State

- Serialize lifecycle phase and countdown state.
- Restore or normalize lifecycle phase deterministically.
- Avoid inventory clearing when resuming an already-started match.

Acceptance criteria:

- Paused matches resume paused.
- Running matches resume running.
- Start-freeze matches either resume freeze safely or normalize to running by explicit rule.
- Return countdown resumes only if the match genuinely ended before shutdown.

### Stage 5: Clean Registry Lifecycle Model

- Split transient backend commands from durable session status.
- Add request ids/timestamps for stop/seed-change/pause commands.
- Ensure retained relaunch starts a fresh command generation.

Acceptance criteria:

- Stale stop/return flags cannot affect a restored backend.
- Main-server UI still shows correct retained/running/stopped state.
- Backend routing remains compatible with Velocity and direct transfer modes.

## Testing Matrix

Test each gamemode:

- Start new match.
- Play until meaningful state exists.
- Stop main server with console `stop`.
- Relaunch main server.
- Relaunch retained session.
- Confirm world state.
- Confirm inventory.
- Confirm position/dimension.
- Confirm runtime state.
- Confirm no start countdown replay.
- Confirm no unexpected return countdown.
- Confirm autosave after restore still writes valid state.

Per gamemode checks:

- Manhunt: roles, alive/dead runners, hunter compass, runner tracking, respawn queues, lead timer/hunt state.
- Bounty Hunt: scores, targets, grace timer, invincibility, tracker targets, cooldowns.
- Death Swap: swap timer, points, alive set, recent targets, pending assignment, attribution.
- Speedrun: runner UUID, elapsed timer, state, scoreboard.
- Resource Sprint: active objectives, team progress, elapsed timer, sudden death, scores/statistics.

## Open Questions

- Should a saved `START_FREEZE` resume the freeze countdown or normalize to running?
- Should a saved `RETURNING` resume return countdown or require admin confirmation after restart?
- Should every gamemode be required to implement `PersistentMinigame`, or should some modes explicitly opt out of retained restore?
- Should `playerStates` include spectators and unassigned late joiners, or only active participants?
- Should restore diagnostics be admin-visible in the GUI or log-only?
- Should save files be versioned per gamemode, per framework, or both?

## Immediate Next Step

If this plan is approved, implement Stage 1 and Stage 2 first:

- Add explicit backend launch mode.
- Centralize restore branching.
- Add `PersistentMinigame` to Speedrun and Resource Sprint.
- Add player snapshot merge hardening if it is small enough to include safely.

Then run the full restore matrix for all five current gamemodes.
