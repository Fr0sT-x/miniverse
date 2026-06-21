# Miniverse — Migration Plan

> **How to use this file**
> This is the ordered work sequence for moving from the current messy state to a
> clean, framework-consistent codebase. Work top-to-bottom. Do not skip phases.
> Do not start a new step while a previous step's PR is open.
>
> When a step is complete, mark it `[x]` and add the completion date.
> When a step is abandoned or changed, add a note explaining why.
>
> Last updated: **2026-06-20**

---

## Phase A — Stabilise Frameworks (No Gamemode Logic Changes)

All steps in Phase A are isolated, low-risk, single-concern changes.
Complete all of Phase A before touching any gamemode's game logic.

---

### A01 — Fix SpectatorService.onPlayerLeave Session Leak `[BUG B01]`

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `SpectatorService.java` only
**Risk:** Low — single method change

**What:** `onPlayerLeave(player)` correctly removes the leaving player from other
sessions' target lists but does not clean up the leaving player's *own* `SpectatorSession`.

**Fix:** In `SpectatorService.onPlayerLeave(ServerPlayerEntity player)`, after the
loop that updates other sessions, add:
```java
if (this.sessions.containsKey(player.getUuid())) {
    this.stopSpectating(player.getUuid(), SpectatorStopReason.PLAYER_LEFT);
}
```

**Why this must come first:** Every subsequent Death Lifecycle migration depends on
`SpectatorService` correctly cleaning up on player leave. Migrating gamemodes to F05
before fixing B01 embeds the leak into migrated gamemodes.

**Copilot prompt:**
> In `SpectatorService.onPlayerLeave(ServerPlayerEntity player)`, after the existing
> loop that removes the leaving player from other sessions' target lists, add a call
> to clean up the leaving player's own spectator session if one exists. Use
> `SpectatorStopReason.PLAYER_LEFT` as the reason. Do not change any other logic.

---

### A02 — Fix doImmediateRespawn in Duels and MurderMystery `[BUG B02]`

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `DuelsMinigame.java`, `MurderMysteryMinigame.java`
**Risk:** Low — two one-line changes

**What:** Both gamemodes return `GlobalMatchRules.defaults(true, true)` from
`configureGameRules()`, setting `doImmediateRespawn=true`. This races with the
Death Lifecycle Framework's `changeGameMode(SPECTATOR)` call. See Decision D04.

**Fix:**
- `DuelsMinigame.configureGameRules()`: change `defaults(true, true)` → `defaults(true, false)`
- `MurderMysteryMinigame.configureGameRules()`: change `defaults(true, true)` → `defaults(true, false)`

**Do this as one PR** since both are one-line changes with identical reasoning.

**Copilot prompt:**
> In `DuelsMinigame.configureGameRules()` and `MurderMysteryMinigame.configureGameRules()`,
> change `GlobalMatchRules.defaults(true, true)` to `GlobalMatchRules.defaults(true, false)`.
> Do not change anything else. Add a comment above each return: `// doImmediateRespawn=false
> required: framework calls changeGameMode(SPECTATOR) on fatal damage; client must not
> auto-respawn before the framework transition completes. See DECISIONS.md D04.`

---

### A03 — Fix BlockShuffle Double clearAll `[BUG B03]`

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `BlockShuffleMinigame.java` only
**Risk:** Very low — single line removal

**What:** `BlockShuffleMinigame.initialize()` calls `SpectatorService.getInstance().clearAll()`.
`MinigameManager.setActiveMinigame()` already calls this before `initialize()`.

**Fix:** Remove the `SpectatorService.getInstance().clearAll()` call from
`BlockShuffleMinigame.initialize()`.

---

### A04 — Fix BlockShuffle and DeathShuffle Missing onPlayerLeave `[BUG B04]`

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `BlockShuffleMinigame.java`, `DeathShuffleMinigame.java`
**Risk:** Low — additive change

**What:** Neither gamemode implements `onPlayerLeave`. Disconnected players remain in
`activePlayers` indefinitely, blocking win-condition evaluation for the entire remaining
match.

**Fix for each gamemode:** Implement `PlayerLeaveAware.onPlayerLeave(ServerPlayerEntity player)`:
```java
@Override
public void onPlayerLeave(ServerPlayerEntity player) {
    this.activePlayers.remove(player.getUuid()); // or however activePlayers is keyed
    // re-run win condition check
    this.checkWinCondition();
}
```

**Verify:** After removing a player from `activePlayers`, the win condition check should
correctly detect if only one player remains and trigger match end.

---

### A05 — Consolidate GameState Enum `[DECISION D03]`

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `GameState.java` + all gamemodes (one at a time)
**Risk:** Medium — touches many files, but each change is mechanical

**What:** `GameState` has overlapping values. Canonical set decided in D03.

**Migration sequence:**
1. In `GameState.java`, add `@Deprecated` to: `IN_PROGRESS`, `PLAYING`, `WAITING`,
   `FINISHED`, `MATCH_OVER`, `RETURNING`. Do not delete yet.
2. In each gamemode file, replace deprecated values with canonical equivalents.
   Do one gamemode per commit. Compile and test after each.
   - `IN_PROGRESS` → `RUNNING` (MurderMystery, BlockShuffle, DeathShuffle, others)
   - `PLAYING` → `RUNNING` (Duels)
   - `WAITING` → `WAITING_FOR_PLAYERS` (any user)
   - `FINISHED` / `MATCH_OVER` / `RETURNING` → `ENDING` or `STOPPED` (verify per usage)
3. Final commit: delete all `@Deprecated` values from `GameState.java`.

**Copilot prompt for step 2 (repeat per gamemode):**
> In `[GamemodeName]Minigame.java`, replace all uses of the deprecated `GameState`
> values according to this mapping: `IN_PROGRESS → RUNNING`, `PLAYING → RUNNING`,
> `WAITING → WAITING_FOR_PLAYERS`, `FINISHED → STOPPED`, `MATCH_OVER → ENDING`,
> `RETURNING → ENDING`. Do not change any game logic — only the state enum values.
> Add a comment `// GameState consolidated — see DECISIONS.md D03` where each change is made.

---

### A06 — Generate and Commit Architecture Documentation

**Status:** `[x] Complete — 2026-06-21`
**Files changed:** `docs/status/ARCHITECTURE.md`, `docs/status/GAMEMODE_STATUS.md`,
`docs/status/DECISIONS.md`, `docs/status/MIGRATION_PLAN.md`
**Risk:** None

**What:** Commit the four architecture doc files generated from the audit.
These must be in the repo before any AI-assisted session continues, so that future
Copilot sessions can be given accurate context.

---

## Phase B — Death Lifecycle Framework Migrations

Migrate gamemodes to F05 in this exact order. Do not reorder without updating D05/D06.
Each migration is its own PR. No gamemode logic changes — only death handling re-routing.

**Pre-condition:** Phase A must be fully complete before Phase B begins.

The migration steps for each gamemode follow a fixed pattern:
1. Implement `DeathAwareMinigame` on the gamemode class.
2. Add `private DeathLifecycleManager deathLifecycleManager;` field.
3. In `onMatchStart()`, build `DeathLifecycleConfig` and initialise the manager.
4. In `onMatchEnd()`, call `deathLifecycleManager.handleMatchEnding(lookup)`.
5. In `onPlayerLeave()`, call `deathLifecycleManager.handleDisconnect(player)`.
6. Route death events through `deathLifecycleManager.handleFatalDamage()`.
7. Remove all direct `SpectatorService.startSpectating()` calls that were handling death.
8. Verify `doImmediateRespawn = false` in `configureGameRules()` if `interceptsRespawn = true`.

---

### B01 — Complete MurderMystery F05 Migration

**Status:** `[x] Complete — 2026-06-21`
**Preconditions:** A01, A02, A06 complete
**Files changed:** `MurderMysteryMinigame.java`, verify `MurderMysteryDeathCallbacks.java`,
`MurderMysteryDeathPolicy.java`, `MurderMysterySpectatorPolicy.java`, `MurderMysteryRespawnStrategy.java`

**What:** MurderMystery is already mostly migrated. This step verifies and completes it.

**Checklist:**
- [x] `doImmediateRespawn = false` in `configureGameRules()` (fixed in A02)
- [x] `deathLifecycleManager` initialised in `onMatchStart()` ✅ (already done)
- [x] `deathLifecycleManager.handleMatchEnding()` called in `onMatchEnd()` — verify
- [x] `deathLifecycleManager.handleDisconnect()` called in `onPlayerLeave()` — verify
- [x] No remaining direct `SpectatorService.startSpectating()` calls for death transitions
- [x] `onPlayerDeath` no-op has a clear comment: `// Handled by DeathLifecycleManager via allowDamage()`
- [x] `GameState.IN_PROGRESS` replaced with `GameState.RUNNING` (from A05)

**Copilot prompt:**
> Review `MurderMysteryMinigame.java` and verify that the Death Lifecycle Framework
> integration is complete according to the checklist in MIGRATION_PLAN.md B01.
> For any missing item, add the required code. Do not change game logic — only
> lifecycle wiring. Add `// Death Lifecycle Framework — see ARCHITECTURE.md F05`
> above the `deathLifecycleManager` field declaration.

---

### B02 — Migrate Speedrun to F05

**Status:** `[ ] Not started`
**Preconditions:** B01 complete
**Files changed:** `SpeedrunMinigame.java` + new `SpeedrunDeathPolicy.java`,
`SpeedrunSpectatorPolicy.java`, `SpeedrunRespawnStrategy.java` (or use built-in policies)

**What:** Speedrun's only death scenario: runner entity death → spectate forever.
This is the simplest possible F05 migration.

**Policy decisions:**
- `DeathPolicy`: clear inventory if needed; `interceptsRespawn = true`
- `DeathSpectatorPolicy`: free-fly spectator targeting remaining runners
- `PostDeathPolicy`: `SpectateForeverPolicy` (built-in)
- `RespawnStrategy`: teleport to death location (runner is eliminated)
- `configureGameRules()`: `keepInventory=true, doImmediateRespawn=false` ✅ (already correct)

**Remove:** Inline `onEntityDeath` death handling; direct `SpectatorService` call.

---

### B03 — Migrate BountyHunt to F05

**Status:** `[ ] Not started`
**Preconditions:** B02 complete
**Files changed:** `BountyHuntMinigame.java` + new death policy/strategy files

**What:** BountyHunt has all-vs-all PvP death. Players respawn after a timer.
The invincibility/grace period overlay should move to `DeathLifecycleCallbacks.onRespawnComplete`.

**Policy decisions:**
- `DeathPolicy`: drop bounty item, update scores; `interceptsRespawn = true`
- `DeathSpectatorPolicy`: free-fly, targeting active players
- `PostDeathPolicy`: timed respawn (configurable countdown)
- `RespawnStrategy`: random spawn point
- `configureGameRules()`: `keepInventory=false, doImmediateRespawn=false` ✅ (already correct)

**Remove:** Inline death handling in `onEntityDeath`; direct `SpectatorService` call;
`announcedGraceThresholds` set (move to F19 CountdownService in Phase C).

---

### B04 — Migrate DeathSwap to F05 (Retire F22)

**Status:** `[ ] Not started`
**Preconditions:** B03 complete
**Files changed:** `DeathSwapMinigame.java`; delete `RespawnPolicyController.java`

**What:** DeathSwap's ELIMINATION/POINTS modes map cleanly to F05.

**Policy decisions:**
- `DeathPolicy`: mark player eliminated or decrement lives; `interceptsRespawn = true`
- `DeathSpectatorPolicy`: free-fly targeting active players
- `PostDeathPolicy`: `SpectateForeverPolicy` for ELIMINATION; `TimedRespawnPolicy` for POINTS
- `RespawnStrategy`: random spawn point
- `configureGameRules()`: `keepInventory=true, doImmediateRespawn=false` ✅ (already correct)

**On completion:** Delete `RespawnPolicyController.java` and `RespawnMode.java` in the same PR.
These have no remaining users.

---

### B05 — Migrate Infection to F05

**Status:** `[ ] Not started`
**Preconditions:** B04 complete
**Files changed:** `InfectionMinigame.java` + new death policy files

**What:** Infection's death mechanic is unique — death converts the player to the
infected team rather than eliminating them. This is an F05 use case where `DeathPolicy.execute()`
runs the conversion logic.

**Policy decisions:**
- `DeathPolicy`: convert player to infected team; give infected kit/items; `interceptsRespawn = true`
- `DeathSpectatorPolicy`: brief spectator transition before respawn (or none if instant)
- `PostDeathPolicy`: short timer (e.g. 3 seconds), then respawn as infected
- `RespawnStrategy`: infected spawn point
- `configureGameRules()`: `keepInventory=false, doImmediateRespawn=false` ✅ (already correct)

**Note:** After this migration, consider also adopting F15 Role and F16 Visibility
for the survivor/infected role tracking (see Phase C step C05).

---

### B06 — Migrate Bridge to F05

**Status:** `[ ] Not started`
**Preconditions:** B05 complete
**Files changed:** `BridgeMinigame.java` + new death policy files

**What:** Bridge uses immediate respawn at team spawn with score update on kill.
`doImmediateRespawn=true` is currently intentional but must change to `false` for F05.

**Policy decisions:**
- `DeathPolicy`: update kill/death scores; `interceptsRespawn = true`
- `DeathSpectatorPolicy`: very brief (0–1 second) or none, then immediate respawn
- `PostDeathPolicy`: `ImmediateRespawnPolicy` (may need to be written)
- `RespawnStrategy`: team spawn point
- `configureGameRules()`: change `doImmediateRespawn=true` → `false`

**Note:** The "feel" of immediate respawn is preserved via `ImmediateRespawnPolicy`
which calls `executeRespawn()` immediately from `start()`.

---

### B07 — Migrate Manhunt to F05 (Last)

**Status:** `[ ] Not started`
**Preconditions:** B06 complete, `ManhuntSpeedrunnerRespawnSystem` fully understood
**Files changed:** `ManhuntMinigame.java`, `ManhuntSpeedrunnerRespawnSystem.java` (major refactor)

**What:** Manhunt is the most complex migration. `ManhuntSpeedrunnerRespawnSystem` is a
parallel death/respawn framework with its own tick loop, persistence, reconnect handling,
and respawn protection. All of this must map to F05 policy objects without regressions.

**Before starting this step:**
- Read `ManhuntSpeedrunnerRespawnSystem.java` in full.
- Map every feature to a F05 policy/callback equivalent.
- Present the mapping for review before writing any code.
- Confirm no edge case is silently dropped (reconnect handling is especially risky).

**Policy decisions:** To be determined after full system read — do not assume.

---

## Phase C — Gamemode Cleanup and Standardisation

Phase C steps can be done in any order, but only after all Phase B steps for the
relevant gamemodes are complete. Each step is its own PR.

---

### C01 — Adopt CountdownService in All Gamemodes That Reimplement It

**Status:** `[ ] Not started`
**Files changed:** `BountyHuntMinigame.java`, `ResourceSprintMinigame.java`,
`BlockShuffleMinigame.java`, `DeathShuffleMinigame.java`, `BridgeMinigame.java`

**What:** Replace `Set<Integer> timeWarningsShown` / `announcedGraceThresholds` patterns
with `CountdownService.announceOnce()` and `CountdownService.announceVisibleCountdown()`.

**Do one gamemode per commit.**

---

### C02 — Rewrite BlockShuffle

**Status:** `[ ] Not started`
**Preconditions:** A03, A04, C01 complete for BlockShuffle; B05 (F05 adoption) complete

**What:** BlockShuffle's round logic shares >80% with DeathShuffle. After fixing all
confirmed bugs and adopting CountdownService, do a targeted rewrite focused on:
1. Extracting round management to a shared `RoundObjectiveFramework` (see D09).
2. Cleaning up the remaining direct framework calls.

**Do not rewrite in one PR.** Split into: (a) extract shared round logic to interface,
(b) rewrite BlockShuffle consuming the interface, (c) rewrite DeathShuffle consuming
the interface.

---

### C03 — Rewrite DeathShuffle

**Status:** `[ ] Not started`
**Preconditions:** C02 complete

**What:** Same as C02. DeathShuffle consumes the `RoundObjectiveFramework` extracted
in C02. Verify `activePlayers` disconnect handling is correct after rewrite.

---

### C04 — Adopt TeamManager in Duels

**Status:** `[ ] Not started`
**Preconditions:** B01 (Duels F05 migration complete)

**What:** Replace `List<ServerPlayerEntity> team1, team2` with `TeamManager`.
Implement `TeamManagerProvider` on `DuelsMinigame`. This enables `ChatRouter` to route
team chat correctly and enables `VanillaTeamAdapter` sync for Duels.

---

### C05 — Adopt Role and Visibility Frameworks in Infection

**Status:** `[ ] Not started`
**Preconditions:** B05 (Infection F05 migration) complete

**What:** Replace `Set<UUID> survivors / infected` with `RoleManager`.
Replace raw vanilla team visibility settings with `VisibilityManager`.

---

### C06 — Add Scoreboard to Duels

**Status:** `[ ] Not started`
**Preconditions:** C04 complete

**What:** Duels has no sidebar scoreboard. Add a `ScoreboardTemplate` showing
current round, score, and match progress.

---

### C07 — Improve Persistent Session Coverage

**Status:** `[ ] Not started`

**What:** Review each gamemode with a `⚠️` in F06 and either implement a meaningful
`saveRuntimeState()` override or add a code comment explaining why persistence is
intentionally not needed (so the silence is intentional, not forgotten).

**Gamemodes to review:** BlockShuffle, DeathShuffle, MurderMystery

---

## Phase D — New Frameworks (Future)

These are planned frameworks that do not exist yet. Add detail here when work begins.

| ID | Framework | Trigger to start | Dependencies |
|----|-----------|-----------------|--------------|
| D-F24 | Objective Round Framework | After C03 | C02, C03 |
| D-F25 | Win Condition Framework | After all Phase B | All F05 migrations |
| D-F26 | Respawn Protection Framework | After B06 | B06 (Bridge) |
| D-F27 | Virtual Economy Framework | New social gamemode added | None |

---

## Phase 7.5 — Singleton Eradication (Deferred)

**Status:** `[ ] Deferred until all Phase B complete`
**Decision:** D07

Convert singletons to constructor-injected instances in this order:
1. `MinigameManager` (most referenced — largest change)
2. `SpectatorService`
3. `FreezeService`
4. `RegionTriggerService`

Each is its own PR. No gamemode logic changes in these PRs.

---

## Completion Criteria

The migration is "done" when:
- [ ] All Phase A steps complete
- [ ] All Phase B steps complete (all 11 gamemodes on F05 or have a documented exemption)
- [ ] All Phase C steps complete
- [ ] `GameState` enum has no deprecated aliases
- [ ] `RespawnPolicyController` is deleted
- [ ] No `// TODO: Migrate` comments remain in source
- [ ] All 4 doc files are current and accurate

---

## How to Update This File

When completing a step:
1. Change `[ ] Not started` → `[x] Complete — YYYY-MM-DD`
2. If a step is partially done: `[~] In progress — started YYYY-MM-DD`
3. If a step is abandoned: `[!] Abandoned — reason: <reason>. Superseded by: <new step>`
4. Add any discovered issues as sub-bullets under the step.
5. If a new step is needed, append it to the appropriate phase section.
