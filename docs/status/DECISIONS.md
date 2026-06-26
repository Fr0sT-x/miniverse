# Miniverse — Architectural Decisions

> **How to use this file**
> Every significant architectural choice lives here. When an AI session asks
> "why is it done this way?" — the answer is in this file. When a design choice
> is made, add an entry before writing any code. Entries are never deleted;
> if a decision is reversed, add a new entry that supersedes the old one.
>
> **Format:** Each entry has an ID (D01, D02 …), a status, and a date.
> AI sessions must not contradict a DECIDED entry without explicit instruction.
>
> Last updated: **2026-06-20**

---

## Index

| ID | Title | Status | Date |
|| D16 | F07 Global Match Rules Framework Removed | DECIDED | 2026-06-26 |
----|-------|--------|------|
| D01 | AbstractMinigame as universal base class | DECIDED | 2026-06-20 |
| D02 | Framework-first: opt-in interfaces over identity checks | DECIDED | 2026-06-20 |
| D03 | GameState enum consolidation required | DECIDED | 2026-06-20 |
| D04 | doImmediateRespawn must be false when using Death Lifecycle with interceptsRespawn=true | DECIDED | 2026-06-20 |
| D05 | Manhunt migrates to Death Lifecycle last | DECIDED | 2026-06-20 |
| D06 | MurderMystery migrates to Death Lifecycle before Manhunt | DECIDED | 2026-06-20 |
| D07 | Singleton eradication (Phase 7.5) is deferred | DECIDED | 2026-06-20 |
| D08 | DeathPolicy.execute() handles inventory regardless of keepInventory gamerule | DECIDED | 2026-06-20 |
| D09 | Block Shuffle and Death Shuffle share Objective Round logic — extraction pending | DECIDED | 2026-06-20 |
| D10 | RespawnPolicyController is deprecated by DeathLifecycleManager | DECIDED | 2026-06-20 |
| D11 | VanillaTeamAdapter is display-only; TeamManager is authoritative | DECIDED | 2026-06-20 |
| D12 | deathLifecycleManager initialised in onMatchStart, not initialize() | DECIDED | 2026-06-20 |
| D13 | PlayerTransferService retry gap must be fixed before session routing work | DECIDED | 2026-06-20 |
| D14 | Framework bugs are fixed before gamemode migration begins | DECIDED | 2026-06-20 |
| D15 | Speedrun is Not an Elimination Gamemode (Death is a Non-Event) | DECIDED | 2026-06-22 |

---

## Decision Records

---

### D01 — AbstractMinigame as Universal Base Class

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** All gamemodes extend `AbstractMinigame`. No gamemode implements
`Minigame` directly.

**Reason:** `AbstractMinigame` provides ~15 cross-cutting concerns automatically:
lifecycle hooks (`startGame`/`stopGame` are `final`), gamerule application, scoreboard
init, team sync, `FrameworkModule` auto-cleanup, and opt-in interface dispatch through
`MinigameEventRouter`. This eliminates enormous amounts of boilerplate and ensures
framework-level concerns cannot be bypassed by individual gamemodes.

**Constraints this imposes:**
- `startGame()` and `stopGame()` are `final`. Gamemodes override `onMatchStart()`
  and `onMatchEnd()` only.
- `configureGameRules()` is abstract — every gamemode must implement it.
- `FrameworkModule` instances registered via `getOrRegisterModule()` are cleaned up
  automatically. Do not call `cleanup()` on them manually.

**Supersedes:** Nothing.

---

### D02 — Framework-First: Opt-In Interfaces Over Identity Checks

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** Framework code must never check for a specific gamemode's type or identity.
If a gamemode needs special framework behaviour, it implements an opt-in interface and
`MinigameEventRouter` dispatches polymorphically.

**Reason:** Identity checks (`instanceof ManhuntMinigame`, `gamemode.equals("manhunt")`)
create invisible coupling between the framework and gamemodes, making both harder to
change and test. Opt-in interfaces are checked once in the router and the logic stays
in the right layer.

**Examples of correct patterns:**
```java
// ✅ Correct — polymorphic opt-in
if (active instanceof DeathAwareMinigame d) { d.getDeathLifecycleManager().tick(server); }
if (active instanceof PlayerRegionAware r)  { regionTriggerService.dispatch(r, player); }

// ❌ Wrong — identity check
if (active instanceof ManhuntMinigame) { /* manhunt-specific logic */ }
```

**Supersedes:** Nothing.

---

### D03 — GameState Enum Consolidation Required

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** The `GameState` enum has redundant values that mean the same phase.
These must be consolidated before any further gamemode work.

**Problem:** Three values mean "actively running" (`RUNNING`, `IN_PROGRESS`, `PLAYING`).
Code that checks `state == GameState.RUNNING` silently misses BlockShuffle and
DeathShuffle which use `IN_PROGRESS`. Similarly, `WAITING` and `WAITING_FOR_PLAYERS`
are aliases, as are `FINISHED`, `ENDING`, `MATCH_OVER`, `RETURNING`.

**Canonical set (decided):**

| Canonical | Replaces | Meaning |
|-----------|----------|---------|
| `WAITING_FOR_PLAYERS` | `WAITING` | Pre-game; not enough players |
| `STARTING` | (existing) | Countdown in progress |
| `RUNNING` | `IN_PROGRESS`, `PLAYING` | Match is live |
| `PAUSED` | (existing) | Admin pause |
| `ENDING` | `FINISHING`, `MATCH_OVER` | Post-game; return sequence |
| `STOPPED` | `FINISHED`, `RETURNING` | Session complete |

**Migration plan:** Add `@Deprecated` annotation to all alias values → update all
gamemode state assignments one at a time → delete alias values in a final cleanup
commit. The project must compile after each step.

**Supersedes:** Nothing.

---

### D04 — doImmediateRespawn Must Be False When interceptsRespawn=true

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** Any gamemode using `DeathLifecycleManager` with a `DeathPolicy` where
`interceptsRespawn()` returns `true` must return `doImmediateRespawn = false` from
`configureGameRules()`.

**Reason:** When `doImmediateRespawn = true`, the Minecraft client automatically sends
a respawn packet without the death screen. The Death Lifecycle Framework calls
`changeGameMode(SPECTATOR)` immediately on fatal damage — before the client has
processed a death screen. If `doImmediateRespawn = true`, the client auto-respawns at
the same server tick, creating a race: the player may momentarily enter SURVIVAL at
world spawn before the framework's SPECTATOR transition takes effect.

**Affected gamemodes right now (bugs B02):**
- `MurderMysteryMinigame.configureGameRules()` → `defaults(true, true)` → change to `defaults(true, false)`
- `DuelsMinigame.configureGameRules()` → `defaults(true, true)` → change to `defaults(true, false)`

**Rule:** `interceptsRespawn = true` + `doImmediateRespawn = true` = always a bug.

**Supersedes:** Nothing.

---

### D05 — Manhunt Migrates to Death Lifecycle Last

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** Manhunt is the last gamemode to migrate to the Death Lifecycle Framework,
after all other gamemodes.

**Reason:** `ManhuntSpeedrunnerRespawnSystem` is a bespoke parallel death/respawn
framework with its own tick loop, persistence layer, reconnect handling, and respawn
protection timers. These do not map cleanly to the Death Lifecycle Framework's policy
model without regressions. Migrating Manhunt requires understanding every edge case
in that system first.

**Correct migration sequence for F05 (from MIGRATION_PLAN.md):**
MurderMystery → Speedrun → BountyHunt → DeathSwap → Infection → Bridge → Manhunt

**Supersedes:** Nothing.

---

### D06 — MurderMystery Is the First Gamemode to Complete F05 Migration

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** MurderMystery (not Manhunt or Duels) is the first gamemode to be fully
cleaned up and verified on the Death Lifecycle Framework.

**Reason:** Duels is already fully wired and serves as the reference. MurderMystery is
already partially migrated and has the next simplest death model (spectate-forever on
death, no respawn). Completing MurderMystery's migration (mainly fixing B02 and
verifying the `onMatchEnd` cleanup) validates the framework seams before touching
gamemodes with more complex death flows.

**What "complete" means for MurderMystery F05:**
1. B02 fixed (`doImmediateRespawn = false`).
2. `deathLifecycleManager.handleMatchEnding()` called in `onMatchEnd()` — verify.
3. No remaining `SpectatorService.startSpectating()` calls for death transitions.
4. `onPlayerDeath` no-op comment updated to remove "TODO: Migrate" if present.

**Supersedes:** Nothing.

---

### D07 — Singleton Eradication (Phase 7.5) Is Deferred

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** The conversion of `MinigameManager`, `SpectatorService`, `FreezeService`,
and `RegionTriggerService` from singletons to constructor-injected instances is deferred
until all Death Lifecycle Framework migrations are complete.

**Reason:** Singleton eradication touches 15+ shared files simultaneously. Executing it
concurrently with Death Lifecycle migration would violate the one-concern-per-PR rule
and create an unmanageable regression surface. The `// TODO: Migrate — remove singleton`
comments already in source are the tracking mechanism; they must not be removed until
Phase 7.5 begins.

**What "Phase 7.5" means:**
- Dedicated milestone, isolated from all gamemode work.
- `MinigameManager` converted first (most referenced).
- `SpectatorService`, `FreezeService`, `RegionTriggerService` follow in order.
- No gamemode code changes in Phase 7.5 PRs.

**Supersedes:** Nothing.

---

### D08 — DeathPolicy.execute() Handles Inventory Regardless of keepInventory Gamerule

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** When the Death Lifecycle Framework intercepts a player's death
(`interceptsRespawn = true`), inventory management is handled by `DeathPolicy.execute()`,
not by the `keepInventory` gamerule. The gamerule has no effect on deaths processed
by the framework.

**Reason:** `interceptsRespawn = true` bypasses the vanilla death event entirely. Vanilla
never runs its own death code, so `keepInventory` is never consulted. This means:
- If `DeathPolicy.execute()` calls `player.getInventory().clear()`, the player loses
  their inventory regardless of `keepInventory`.
- If `DeathPolicy.execute()` does not clear inventory, the player keeps it regardless
  of `keepInventory`.

**Implication for configureGameRules():** The `keepInventory` value in `GlobalMatchRules`
is still meaningful for context (affects vanilla respawns that are NOT intercepted) but
does not control the framework death path. Do not rely on it to determine inventory
behaviour in framework-controlled deaths.

**Supersedes:** Nothing.

---

### D09 — Block Shuffle and Death Shuffle Share Round Logic — Extraction Pending

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** Block Shuffle and Death Shuffle share >80% of their round logic and are
candidates for extraction into a shared Objective Round Framework. Neither gamemode
should receive new round-management features until the extraction is done.

**Shared logic identified:**
- `RoundState` enum (INTERMISSION / ACTIVE)
- Per-player objective assignment map
- Active player tracking
- Round timer ticks
- Time warnings (`timeWarningsShown`)
- Sudden death logic
- Point scoring
- Scoreboard line update pattern

**Proposed framework:** `RoundObjectiveFramework` with `ObjectiveAssigner<T>`,
`RoundPhaseController`, and a `RoundAwareMinigame` interface.

**Current action:** Both gamemodes are marked as "candidate for rewrite" in
MIGRATION_PLAN.md Phase C. No new round features in either until extraction is planned.

**Supersedes:** Nothing.

---

### D10 — RespawnPolicyController Is Deprecated by DeathLifecycleManager

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** `RespawnPolicyController` is a pre-framework predecessor to
`DeathLifecycleManager`. It must not be adopted by any new or migrating gamemode.
It will be deleted once DeathSwap is migrated to F05.

**Current only user:** DeathSwap.

**Migration path:** When DeathSwap adopts F05, `RespawnPolicyController` is deleted
in the same PR.

**Supersedes:** Nothing.

---

### D11 — VanillaTeamAdapter Is Display-Only; TeamManager Is Authoritative

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** `TeamManager` is the authoritative source of team membership. `VanillaTeamAdapter`
is a one-way mirror for display purposes (nametag colour, friendly fire in Minecraft).
No code should read team membership from the Minecraft scoreboard.

**Reason:** Minecraft scoreboard team membership is a client-sync mechanism, not a game
logic mechanism. Routing game logic through it creates hidden coupling to Minecraft's
own team state, which can drift from the logical state.

**Correct pattern:**
```java
// ✅ Read team from TeamManager
teamManager.getTeam(player)

// ❌ Read team from scoreboard
player.getScoreboardTeam()
```

**`ChatRouter`** always reads from `TeamManagerProvider`, never from the scoreboard.

**Supersedes:** Nothing.

---

### D12 — DeathLifecycleManager Is Initialised in onMatchStart(), Not initialize()

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** `DeathLifecycleManager` is constructed inside `onMatchStart()`, not in
`initialize()` or the constructor.

**Reason:** `DeathLifecycleConfig` implementations often capture live objects (e.g.
`matchManager` in Duels) that do not exist until `onMatchStart()` runs. Constructing
the manager earlier would require null-checking captured references on every death.

**Consequence:** `getDeathLifecycleManager()` returns null between `initialize()` and
`onMatchStart()`. Code that calls it must null-check. `MinigameEventRouter` already
does this: it checks `manager != null` before calling `manager.tick()`. Deaths that
occur before `onMatchStart()` (pre-game) fall through to the `AbstractMinigame` no-op
`onPlayerDeath()` — this is acceptable and should be documented in the source.

**Supersedes:** Nothing.

---

### D13 — PlayerTransferService Retry Gap Must Be Fixed Before Session Routing Work

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** New session routing features must not be added until the two confirmed
session routing bugs are fixed.

**Bugs:**
1. `PlayerTransferService.transferAssignedPlayers` is fire-and-forget — players with
   connection blips during backend boot are silently dropped.
2. `GameSession.withoutMember`/`addMemberToGroup` copy `BackendInstance` by value.
   A rebuild racing with `markRunning` permanently freezes the group at `LAUNCHING`.

**Planned fix:**
- `withPlannedTeam` factory sharing a live `BackendInstance` reference (removes copy).
- `handleMissedTransfers` proactive retry in `SessionRoutingEvents` with per-player
  rate limiting and WARN-level logging.

**Supersedes:** Nothing.

---

### D14 — Framework Bugs Fixed Before Gamemode Migration Begins

**Status:** DECIDED
**Date:** 2026-06-20

**Decision:** Framework-level bugs (B01–B04 as of this writing) must be fixed in
isolated PRs before any gamemode migration to those frameworks proceeds.

**Reason:** Migrating a gamemode to a buggy framework embeds the bug in the gamemode's
code. When the framework bug is later fixed, the gamemode may need re-migration.
Fix the framework first; migrate clean.

**Current pre-migration required fixes:**
- B01: `SpectatorService.onPlayerLeave` session leak — must fix before any F05 migration
- B02: `doImmediateRespawn=true` in Duels and MurderMystery — fix in those two gamemodes
- B03: `BlockShuffle.initialize()` double clearAll — fix before any BlockShuffle changes
- B04: BlockShuffle/DeathShuffle missing `onPlayerLeave` — fix before any round logic changes

**Supersedes:** Nothing.

---

### D15 — Speedrun is Not an Elimination Gamemode

**Status:** DECIDED
**Date:** 2026-06-22

**Decision:** Speedrun's death-and-respawn behavior is intentionally unchanged (death is a non-event — respawn and keep running). The F05 migration routes the fatal-damage event through `DeathLifecycleManager` only to provide a framework home for callbacks, not to enforce elimination or spectator mechanics.

**Reason:** Speedrun is about time to completion; dying is just a time penalty. `interceptsRespawn` is set to `false`.

**Supersedes:** Nothing.

---

## Adding a New Decision

Copy this template and append it to the file:

```markdown
### DXX — Short Title

**Status:** [PROPOSED | DECIDED | SUPERSEDED BY DYY]
**Date:** YYYY-MM-DD

**Decision:** One sentence stating what was decided.

**Reason:** Why this decision was made. What alternatives were considered and rejected.

**Constraints this imposes:** (optional) What this decision prevents or requires.

**Supersedes:** DYY (or "Nothing")
```

**Status values:**
- `PROPOSED` — under discussion, not yet locked in
- `DECIDED` — locked in; AI sessions must not contradict this
- `SUPERSEDED BY DXX` — replaced by a newer decision; kept for history
### D16 — F07 Global Match Rules Framework Removed

**Status:** DECIDED
**Date:** 2026-06-26

**Decision:** The F07 Global Match Rules Framework, along with its associated UI components, has been completely removed from the codebase.

**Reason:** `configureGameRules()` allowed server administrators to toggle structurally mandatory gamerules (like `KEEP_INVENTORY` or `DO_IMMEDIATE_RESPAWN`) via the workspace UI. This routinely broke gamemodes. Gamemodes now strictly rely on explicitly applying mandatory gamerules via `applyVanillaGameRule()` during their `initialize()` sequence and defaulting to vanilla server properties for everything else.

**Supersedes:** Nothing.

