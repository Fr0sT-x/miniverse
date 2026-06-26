# Miniverse — Architecture Reference

> **How to use this file**
> This is the authoritative description of Miniverse's framework layer.
> Before starting any AI-assisted coding session, paste the relevant framework
> section(s) into context. After finishing a meaningful change, update this file.
> When a framework changes, update its entry here — do not leave stale docs.
>
> Last audited against live code: **2026-06-20**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Core Principles](#2-core-principles)
3. [Layer Map](#3-layer-map)
4. [Framework Reference](#4-framework-reference)
   - [F01 Session Framework](#f01-session-framework)
   - [F02 Match Lifecycle Framework](#f02-match-lifecycle-framework)
   - [F03 Freeze Framework](#f03-freeze-framework)
   - [F04 Spectator Framework](#f04-spectator-framework)
   - [F05 Death Lifecycle Framework](#f05-death-lifecycle-framework)
   - [F06 Persistent Session Framework](#f06-persistent-session-framework)
   - [F07 Global Match Rules Framework (Retired)](#f07-global-match-rules-framework)
   - [F08 Team Framework](#f08-team-framework)
   - [F09 Map Protection Framework](#f09-map-protection-framework)
   - [F10 Region Trigger Framework](#f10-region-trigger-framework)
   - [F11 Map Editor Framework](#f11-map-editor-framework)
   - [F12 Scoreboard Framework](#f12-scoreboard-framework)
   - [F13 Protected Items Framework](#f13-protected-items-framework)
   - [F14 Kit Framework](#f14-kit-framework)
   - [F15 Role Framework](#f15-role-framework)
   - [F16 Visibility Framework](#f16-visibility-framework)
   - [F17 Corpse Framework](#f17-corpse-framework)
   - [F18 Arena Framework](#f18-arena-framework)
   - [F19 Countdown Service](#f19-countdown-service)
   - [F20 Player Snapshot Framework](#f20-player-snapshot-framework)
   - [F21 Derangement/Swap Framework](#f21-derangementswap-framework)
   - [F22 Respawn Policy Framework](#f22-respawn-policy-framework)
   - [F23 Inventory Layout Framework](#f23-inventory-layout-framework)
5. [Confirmed Bugs](#5-confirmed-bugs)
6. [Known Framework Interactions](#6-known-framework-interactions)
7. [Adding a New Framework](#7-adding-a-new-framework)

---

## 1. Project Overview

**Miniverse** is a Fabric 1.21.1 multiplayer minigame platform. It runs on dynamically
spawned backend servers routed through a Velocity proxy. Players connect to a central
lobby; the lobby spawns a backend, routes players to it, and the backend runs one
minigame session.

**Package root:** `dev.frost.miniverse`
**Repository:** `github.com/Fr0sT-x/miniverse` (branch: `master`)
**Primary AI workflow:** Copilot for code generation · Claude for architecture review

**11 active gamemodes:** Manhunt, Speedrun, BountyHunt, DeathSwap, ResourceSprint,
BlockShuffle, DeathShuffle, Duels, MurderMystery, Bridge, Infection

---

## 2. Core Principles

These are locked-in, non-negotiable rules. AI sessions must not violate them.

| # | Principle | Meaning |
|---|-----------|---------|
| P1 | **Framework-first** | Cross-cutting concerns (death, spectating, teams, rules) are solved once in a framework and consumed via opt-in interfaces. Never duplicate logic across gamemodes. |
| P2 | **One concern per PR** | Never mix a framework change with a gamemode change. Never mix two framework changes. |
| P3 | **No identity checks** | Never write `instanceof ManhuntMinigame` or `gamemode.equals("manhunt")` in framework code. Use opt-in interfaces instead. |
| P4 | **No new singletons** | All new dependencies are constructor-injected. Existing singletons (`MinigameManager`, `SpectatorService`, `FreezeService`) are flagged for future removal but not touched until Phase 7.5. |
| P5 | **Compile at every step** | When a framework change breaks multiple files, list every affected file and migrate them one at a time. The project must compile after every commit. |
| P6 | **Resolve ambiguity first** | Never silently assume. If a design question is open, it is a blocker — resolve it before writing code. |
| P7 | **No silent regressions** | Policy or provider choices that silently drop existing player-facing behaviour are bugs in the plan, not acceptable simplifications. |
| P8 | **Dead code out in the same PR** | Temporary placeholders must be `// TODO: Migrate — <description>`. Never leave silent empty stubs. |

---

## 3. Layer Map

```
┌─────────────────────────────────────────────────────────┐
│                    GAMEMODE LAYER                       │
│  ManhuntMinigame, DuelsMinigame, MurderMysteryMinigame  │
│  (extend AbstractMinigame, opt into framework interfaces)│
└────────────────────┬────────────────────────────────────┘
                     │ extends
┌────────────────────▼────────────────────────────────────┐
│                 AbstractMinigame                        │
│  Universal base: lifecycle hooks, gamerule application, │
│  scoreboard init, team sync, module auto-cleanup        │
└────────────────────┬────────────────────────────────────┘
                     │ depends on
┌────────────────────▼────────────────────────────────────┐
│               FRAMEWORK SERVICES                        │
│  SpectatorService · FreezeService · ProtectedItemService│
│  RegionTriggerService · DeathLifecycleManager           │
│  TeamManager · VanillaTeamAdapter · ScoreboardTemplate  │
└────────────────────┬────────────────────────────────────┘
                     │ routed through
┌────────────────────▼────────────────────────────────────┐
│            MinigameEventRouter                          │
│  Registers all Fabric events; dispatches to the active  │
│  minigame and framework services in a defined order     │
└────────────────────┬────────────────────────────────────┘
                     │ drives
┌────────────────────▼────────────────────────────────────┐
│             SESSION LAYER (lobby-side)                  │
│  SessionRegistry · PlayerTransferService                │
│  SessionBootstrapper · SessionRecoveryService           │
└─────────────────────────────────────────────────────────┘
```

**Event dispatch order in `MinigameEventRouter`:**
1. Framework services (SpectatorService, ProtectedItemService, etc.)
2. `MatchLifecycleController`
3. Active `Minigame` opt-in interfaces (`DeathAwareMinigame`, `ServerTickAware`, etc.)

---

## 4. Framework Reference

---

### F01 Session Framework

**Status:** Mostly Implemented · **Adoption:** 11/11 (universal)

**Purpose:** Manages the full lifecycle of a backend server session — creation, boot,
player assignment, routing, state transitions, and crash recovery.

**State machine:** `CREATED → LAUNCHING → RUNNING → STOPPING → STOPPED`

**Key classes:**
| Class | Role |
|-------|------|
| `SessionRegistry` | Disk-based session store; cleanup; snapshots |
| `SessionRoutingEvents` | Fabric event hooks for join/tick/disconnect; missed-transfer retry |
| `PlayerTransferService` | Sends players to backend via Velocity or direct TCP |
| `SessionRuntimeConfig` | Reads `session.properties`/`session.json` for launch context |
| `GameSession` / `SessionGroup` | Session entity model |
| `SessionBootstrapper` | Backend-side; receives join events, ticks session start |
| `SessionRecoveryService` | Restores sessions from disk after crash |

**Known bugs:**
- `PlayerTransferService.transferAssignedPlayers` is fire-and-forget with no retry.
  Players who disconnect during backend boot are silently dropped.
- `GameSession.withoutMember` / `addMemberToGroup` copy `BackendInstance` by value.
  If a rebuild races with `markRunning`, the replacement group freezes at `LAUNCHING`.

> **AI note:** Do not add session transfer logic without addressing the retry gap above.
> The fix is a `handleMissedTransfers` proactive retry in `SessionRoutingEvents` with
> per-player rate limiting, and a `withPlannedTeam` factory sharing a live
> `BackendInstance` reference instead of copying.

---

### F02 Match Lifecycle Framework

**Status:** Fully Implemented · **Adoption:** 11/11 (universal via `AbstractMinigame`)

**Purpose:** Controls the countdown (WAITING → STARTING → RUNNING), end-of-match
return sequence, admin pause/resume, and match title broadcasting.

**Match phase state machine:** `WAITING → STARTING → RUNNING → ENDING → STOPPED`

**Key classes:**
| Class | Role |
|-------|------|
| `MatchLifecycleController` | Countdown, lifecycle player tracking, return flow |
| `MatchLifecycleOptions` | Countdown time, title display config |
| `MatchEndResult` | Encodes outcome for display |
| `MatchProgressionValidator` | Interface: gamemodes can block progression |
| `MatchLifecycleCommands` | Admin: `/match start`, `/match end`, `/match pause` |
| `StandardEndSequence` | Helper to trigger end from inside a gamemode |

**How AbstractMinigame uses it:**
```
startGame() → configureGameRules().apply() → onMatchStart()
stopGame()  → onMatchEnd() → module cleanup → clearParticipants()
```

> **AI note:** `startGame()` and `stopGame()` are `final` in `AbstractMinigame`.
> Gamemodes override `onMatchStart()` and `onMatchEnd()` only.

---

### F03 Freeze Framework

**Status:** Fully Implemented · **Adoption:** 11/11 (universal)

**Purpose:** Freezes players in place (blocks all input) using a custom client packet.
Multi-reason aware — a player stays frozen while any reason is still active.

**Key classes:**
| Class | Role |
|-------|------|
| `FreezeService` | Reason-keyed freeze map; packet sender. Currently a singleton. |
| `FreezeReason` (enum) | `MATCH_START`, `ADMIN_PAUSE`, `SPECTATING`, … |

**Usage pattern:**
```java
FreezeService.getInstance().freeze(player, FreezeReason.MATCH_START);
// ... countdown completes ...
FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_START);
```

**Invariants:**
- A player remains frozen as long as at least one reason is active.
- `onPlayerLeave` correctly removes players.

> **AI note:** This is one of the cleanest services in the codebase. Do not add
> logic to it without strong justification.

---

### F04 Spectator Framework

**Status:** Mostly Implemented · **Adoption:** 11/11 (universally available)
**⚠️ Confirmed bug — see [Confirmed Bugs](#5-confirmed-bugs) B01**

**Purpose:** Manages players in spectator mode — camera control, target selection,
policy enforcement, and session cleanup.

**Key classes:**
| Class | Role |
|-------|------|
| `SpectatorService` | Session CRUD, tick-driven validation, camera updates. Singleton (flagged). |
| `SpectatorSession` | Per-player state: policy, target provider, mode, return mode |
| `SpectatorPolicy` / `SpectatorPolicies` | Pluggable: unrestricted, locked, team-only, fixed-target |
| `SpectatorTargetProvider` / `SpectatorTargetProviders` | Pluggable: roster, online, none, filtered |
| `SpectatorMode` (enum) | `STANDARD`, `ELIMINATED`, `ADMIN` |
| `SpectatorCameraController` | Camera entity attachment |
| `SpectatorEvents` | Listener bus: start, stop, target changed, no-target elimination |

**Lifecycle:**
```
startSpectating() → create SpectatorSession → change to SPECTATOR gamemode
→ attach camera → tick validates targets
→ stopSpectating() → change back to return mode → cleanup
```

**Correct usage from a gamemode (pre-Death Lifecycle migration):**
```java
SpectatorService.getInstance().startSpectating(
    player,
    SpectatorPolicies.unrestricted(),
    SpectatorTargetProviders.roster(context.roster()),
    SpectatorMode.ELIMINATED
);
```

**Preferred usage (after Death Lifecycle migration):**
Do not call `SpectatorService` directly. Configure `DeathSpectatorPolicy` in
`DeathLifecycleConfig` instead. The framework calls `SpectatorService` on your behalf.

> **AI note:** Bug B01 (leaving player's own session not cleaned up) must be fixed
> before any additional Death Lifecycle migration work begins. The fix is one method
> call added to `SpectatorService.onPlayerLeave`.

---

### F05 Death Lifecycle Framework

**Status:** Mostly Implemented · **Adoption:** 3/11 (Duels, MurderMystery, Speedrun)

**Purpose:** Centralises the entire player death flow — fatal damage interception →
inventory/effects cleanup → spectator transition → post-death timer or condition →
respawn location resolution → respawn execution.

**State machine per player:**
```
ALIVE → DEATH_PROCESSING → SPECTATING → RESPAWNING → ALIVE
                                    ↘ DISCONNECTED (any state)
```

**Key classes:**
| Class | Role |
|-------|------|
| `DeathLifecycleManager` | Orchestrator; holds per-player state machines |
| `DeathContext` (record) | Immutable snapshot of death circumstances |
| `DeathState` (enum) | `ALIVE, DEATH_PROCESSING, SPECTATING, RESPAWNING, DISCONNECTED` |
| `CancellationReason` (enum) | `DISCONNECT, MATCH_ENDING, FORCED_REMOVAL, SERVER_SHUTDOWN, ELIMINATION, ADMIN_ACTION, UNKNOWN` |
| `PlayerDeathStateMachine` | Guards valid transitions |
| `DeathLifecycleConfig` (interface) | Gamemode supplies all policies and callbacks |
| `DeathPolicy` (interface) | `execute(player, ctx)` + `interceptsRespawn()` |
| `DeathSpectatorPolicy` (interface) | `apply(player, ctx)` + `requiresFixedCamera()` + `noTargetPolicy()` |
| `PostDeathPolicy` (interface) | `start()`, `cancel(reason)`, `tick(server)` |
| `RespawnStrategy` (interface) | `resolve(ctx, session)` → `RespawnLocation` |
| `DeathLifecycleCallbacks` (interface) | All methods `default {}`: `onDeath`, `onDeathProcessed`, `onSpectatorEnter`, `onSpectatorExit`, `onRespawnBegin`, `onRespawnComplete`, `onDeathFlowCancelled`, `onDeathStateChanged` |
| `DeathAwareMinigame` (interface) | Opt-in: exposes `getDeathLifecycleManager()` |
| `SpectateForeverPolicy` | Built-in `PostDeathPolicy`: player stays in spectator until match ends |

**DeathContext fields:**
```java
UUID victimId, String victimName,
@Nullable Entity killer, DamageSource damageSource,
RegistryKey<World> dimension, Vec3d location,
float yawAtDeath, float pitchAtDeath, long timestamp,
@Nullable String victimTeamId,
@Nullable String matchIdentifier,
@Nullable UUID spectatorTargetAtDeath
```

**How to adopt (gamemode implementation checklist):**
1. Implement `DeathAwareMinigame` on the gamemode class.
2. Add a `private DeathLifecycleManager deathLifecycleManager;` field.
3. Implement `getDeathLifecycleManager()` returning the field.
4. In `onMatchStart()`, construct `DeathLifecycleConfig` (anonymous class or named
   implementation) and `new DeathLifecycleManager(config, SpectatorService.getInstance())`.
5. In `onMatchEnd()`, call `deathLifecycleManager.handleMatchEnding(playerLookup)`.
6. In `onPlayerLeave()`, call `deathLifecycleManager.handleDisconnect(player)`.
7. In `onEntityDeath()` or `allowDamage()`, call `deathLifecycleManager.handleFatalDamage(player, source)`.
8. Remove any direct `SpectatorService.startSpectating()` calls that were previously handling death.
9. In `configureGameRules()`, set `doImmediateRespawn = false` when `interceptsRespawn()` returns true.
   See Known Framework Interactions → [I01](#i01-death-lifecycle-vs-doimmediaterespawn).

**Critical invariant:** `interceptsRespawn = true` requires `doImmediateRespawn = false`.

**Event routing (MinigameEventRouter):**
- `AFTER_RESPAWN` → not directly used by this framework (framework bypasses vanilla respawn)
- `ALLOW_DAMAGE` / `ENTITY_DEATH` → gamemode calls `handleFatalDamage` from its own handler
- Server tick → `MinigameEventRouter.onServerTick` calls `manager.tick(server)` automatically
  when the active minigame is `DeathAwareMinigame`

**Reference implementations:** `DuelsMinigame` · `MurderMysteryMinigame`

> **AI note:** When writing a new `DeathLifecycleConfig`, each `createPostDeathPolicy()`
> call MUST return a new instance — not a shared singleton. Multiple simultaneous
> deaths need independent policy instances.

---

### F06 Persistent Session Framework

**Status:** Partially Implemented · **Adoption:** 8/11 (meaningful: Manhunt, BountyHunt,
DeathSwap, Bridge, Infection; no-op inherited: BlockShuffle, DeathShuffle, Speedrun)

**Purpose:** Saves and restores minigame session state to disk — crash recovery,
admin pause persistence, server restarts.

**Key classes:**
| Class | Role |
|-------|------|
| `PersistentMinigame` (interface) | `saveRuntimeState()`, `loadRuntimeState()`, `saveSessionData()`, `loadSessionData()` |
| `MinigameSessionStore` | Autosave timer (every 30s), save/load orchestrator |
| `PlayerStateStore` / `PlayerStateSnapshot` | Full player state capture and restore |
| `SessionRecoveryService` | On-boot recovery; scans disk, restores active session |

**Gotcha:** `AbstractMinigame.saveRuntimeState()` returns an empty `JsonObject`. Any
gamemode that does not override this is silently saved as `{}`. This is not an error —
it simply means no game state is recovered after a crash. If meaningful mid-game state
must survive restarts, override both `saveRuntimeState()` and `loadRuntimeState()`.

> **AI note:** When adding state to a gamemode that uses `PersistentMinigame`, always
> add serialisation for that state in the same PR.

---

### F07 Global Match Rules Framework

**Status:** Totally retired · **Adoption:** 0/11
**⚠️ Deprecated — completely removed from codebase**

**Purpose:** Pre-framework approach to standardize gamerules. Gamemodes now manually call `applyVanillaGameRule` in their `initialize()` method for structurally mandatory gamerules (like `KEEP_INVENTORY` or `DO_IMMEDIATE_RESPAWN`), relying on server defaults for the rest. 
The associated Workspace UI components to toggle gamerules were also removed to prevent admins from breaking gamemode logic.

**Planned fate:** Already retired. No gamemodes use this anymore. This framework is deleted from the codebase.
---

### F08 Team Framework

**Status:** Fully Implemented · **Adoption:** 6/11

**Purpose:** Manages logical game teams (not scoreboard teams) — membership, metadata,
and vanilla display sync.

**Key classes:**
| Class | Role |
|-------|------|
| `TeamManager` | Team CRUD, player-to-team mapping |
| `GameTeam` | Team entity with member list |
| `TeamRole` (enum) | `MEMBER`, `CAPTAIN`, … |
| `TeamManagerProvider` (interface) | Gamemodes expose their `TeamManager` via this |
| `VanillaTeamAdapter` | Mirrors logical teams into Minecraft scoreboard teams |
| `VanillaTeamDescriptor` / `VanillaTeamOptions` | Config for vanilla team sync |
| `ChatRouter` | Reads `TeamManagerProvider` to route team chat |

**Invariant:** Logical team data is authoritative. Scoreboard teams are display-only.
`ChatRouter` always queries `TeamManagerProvider`, never the scoreboard.

**AbstractMinigame integration:**
- `isTeamBased()` controls whether `VanillaTeamAdapter` is created.
- `syncVanillaTeams()` is called automatically on match start and when teams change.
- `clearVanillaTeams()` is called automatically on match end.

> **AI note:** When adding a new team-based gamemode, implement `TeamManagerProvider`
> on the gamemode class and return your `TeamManager` instance. Do not manage
> scoreboard teams manually.

---

### F09 Map Protection Framework

**Status:** Fully Implemented · **Adoption:** 11/11 (via `MinigameEventRouter`)

**Purpose:** Prevents players from breaking pre-existing map blocks. Only blocks placed
dynamically during the session are breakable.

**Key classes:**
| Class | Role |
|-------|------|
| `MapProtectionManager` | `isProtected(world, pos)`, `canBreak(player, pos, message)` |
| `MapProtectionTracker` | Per-runtime set of dynamically placed block positions |
| `MinigameContext.protectionTracker()` | Accessor |

**Bypass conditions:** OP/creative players, MAP_EDITOR backend mode, gamemodes not
in `MapGamemodeRegistry`.

> **AI note:** This framework requires no gamemode-level code. It runs automatically
> via `MinigameEventRouter`. Do not call it manually from a gamemode.

---

### F10 Region Trigger Framework

**Status:** Fully Implemented · **Adoption:** 1/11 (Bridge only)

**Purpose:** Fires enter/exit callbacks when players move in or out of named map marker
regions. Tick-driven, per-player region tracking.

**Key classes:**
| Class | Role |
|-------|------|
| `RegionTriggerService` | Singleton; tick-driven enter/exit dispatch |
| `RegionRestrictionService` | Queries restriction markers (e.g. `BUILD_DENIED`) |
| `PlayerRegionAware` (interface) | `onPlayerEnterRegion(player, regionId)`, `onPlayerExitRegion(player, regionId)` |
| `RuntimeMarkerCache` | Tick-refreshed active marker cache |

**To adopt:** Implement `PlayerRegionAware` on the gamemode class. No registration needed —
`MinigameEventRouter` dispatches automatically when the active minigame implements the interface.

---

### F11 Map Editor Framework

**Status:** Mostly Implemented · **Adoption:** Confirmed 4/11; partial for 7/11

**Purpose:** In-game schema-driven map editor for placing named markers (spawn points,
regions, goal zones). Each gamemode registers a `MapEditorExtension`.

**Key classes:**
| Class | Role |
|-------|------|
| `MapEditorExtension` | Per-gamemode marker schema |
| `MarkerDefinition` | Schema for a single marker type |
| `MapEditorExtensionRegistry` | Registry of all gamemode extensions |
| `MapEditorMarkerStore` | Persists placed markers to disk |
| `MapEditorValidator` | Validates marker completeness per gamemode |

**Editor lifecycle:** Session launched in `MAP_EDITOR` mode → player places markers
via UI/commands → `MapEditorValidator` checks completeness → backend shuts down.

---

### F12 Scoreboard Framework

**Status:** Fully Implemented · **Adoption:** 10/11 (Duels missing)

**Purpose:** Reusable sidebar scoreboard with updateable lines, per-player show/hide,
and auto-cleanup via `FrameworkModule`.

**Key classes:**
| Class | Role |
|-------|------|
| `ScoreboardTemplate` implements `FrameworkModule` | Sidebar scoreboard lifecycle |
| `ScoreboardLine` | A single updateable row |

**Usage pattern:**
```java
ScoreboardTemplate sb = getOrRegisterModule(new ScoreboardTemplate("My Game"));
ScoreboardLine line = sb.addLine(Text.literal("..."));
sb.show(player);
line.update(Text.literal(newText));  // call from onGameTick
// cleanup is automatic when stopGame() runs
```

**AbstractMinigame auto-cleanup:** All `FrameworkModule` instances registered via
`getOrRegisterModule()` are cleaned up automatically in `stopGame()`.

---

### F13 Protected Items Framework

**Status:** Fully Implemented · **Adoption:** 2/11 (Manhunt, BountyHunt)

**Purpose:** Prevents specific items (tracker compasses, bounty items) from being
dropped, deleted, or held by ineligible players.

**Key classes:**
| Class | Role |
|-------|------|
| `ProtectedItemService` | Singleton; rule registry; tick enforcement |
| `ProtectedItemRule` | Rule: item type, canHold predicate, preventDrop, preventDelete |

**Usage:** Register rules in `onMatchStart()`, clear in `onMatchEnd()`.

---

### F14 Kit Framework

**Status:** Mostly Implemented · **Adoption:** 1/11 (Duels)

**Purpose:** Pre-configured player loadouts (armor, inventory, offhand, effects)
identified by `Identifier`. Supports JSON config files.

**Key classes:** `Kit`, `KitRegistry`, `KitRegistry.loadCustomKits(server)`

**Usage:** `KitRegistry.get(Identifier.of("miniverse", "sword_kit")).ifPresent(kit -> kit.apply(player))`

---

### F15 Role Framework

**Status:** Partially Implemented · **Adoption:** 1/11 (MurderMystery)

**Purpose:** Assigns and manages gameplay roles per player. Roles carry lifecycle
hooks (`onAssign`, `onRemove`).

**Key classes:**
| Class | Role |
|-------|------|
| `RoleManager` implements `FrameworkModule` | Per-player role map; lifecycle hooks |
| `Role` (interface) | `onAssign(player)`, `onRemove(player)`, `isSpectator()` |

**Access:** `AbstractMinigame.roles()` — lazy accessor with auto-cleanup.

**Candidate gamemodes not yet using this:** Infection (survivor/infected),
Manhunt (hunter/speedrunner).

---

### F16 Visibility Framework

**Status:** Partially Implemented · **Adoption:** 1/11 (MurderMystery)

**Purpose:** Controls player name-tag visibility rules between roles.

**Key classes:** `VisibilityManager` (wraps `VanillaTeamAdapter` for role-based visibility)

**Depends on:** F08 Team Framework, F15 Role Framework.

---

### F17 Corpse Framework

**Status:** Partially Implemented · **Adoption:** 1/11 (MurderMystery)

**Purpose:** Spawns a visual corpse (armor stand) at a player's death location.

**Key classes:** `CorpseManager` implements `FrameworkModule`

**Access:** `AbstractMinigame.corpses()` — lazy accessor with auto-cleanup.

---

### F18 Arena Framework

**Status:** Partially Implemented · **Adoption:** 1/11 (Duels)

**Purpose:** Manages physical arena instances — region registration, block state
reset after each round, player-to-arena tracking.

**Key classes:**
| Class | Role |
|-------|------|
| `ArenaManager` | Registry; idle/busy state; block change tracking; reset |
| `Arena` | Instance with state machine: `IDLE → RUNNING → RESETTING → IDLE` |
| `ArenaRegion` | Bounding box definition |

---

### F19 Countdown Service

**Status:** Fully Implemented · **Adoption:** 1/11 (DeathSwap only — others reimplement manually)

**Purpose:** Provides countdown announcement deduplication and visible countdown titles.
Prevents the same countdown second from being announced more than once per phase.

**Key class:** `CountdownService` — `announceOnce()`, `announceVisibleCountdown()`, `reset()`

**Usage:**
```java
// In onGameTick():
countdownService.announceOnce(remainingSeconds, 60, () -> broadcast("60 seconds left!"));
countdownService.announceVisibleCountdown(remainingSeconds, 10); // shows title for ≤10s
```

> **AI note:** Five gamemodes reimplement this manually with a `Set<Integer> timeWarningsShown`
> pattern (BountyHunt, ResourceSprint, BlockShuffle, DeathShuffle, Bridge). These are
> migration candidates tracked in MIGRATION_PLAN.md Phase C.

---

### F20 Player Snapshot Framework

**Status:** Mostly Implemented · **Adoption:** 8/11 (tied to PersistentMinigame)

**Purpose:** Captures and restores full player state (position, gamemode, inventory,
effects, hunger, experience) for crash recovery.

**Key classes:** `PlayerStateSnapshot` (record), `PlayerStateStore`

**Used by:** `MinigameSessionStore` during autosave. Not called per-tick.

---

### F21 Derangement/Swap Framework

**Status:** Fully Implemented · **Adoption:** 1/11 (DeathSwap)

**Purpose:** Shuffle algorithm guaranteeing no player is assigned to themselves,
with penalty scoring to avoid recent repeat pairings.

**Key class:** `DerangementAssignment<T>` — returns `Map<UUID, UUID>` swap assignments.

---

### F22 Respawn Policy Framework

**Status:** Totally retired · **Adoption:** 0/11 
**⚠️ Deprecated by F05 — do not adopt in new gamemodes**

**Purpose:** Pre-framework approach to post-death gamemode management (ELIMINATION vs
POINTS respawn modes). Predates `DeathLifecycleManager`.

**Planned fate:** Already retired. No gamemodes use this anymore. This framework is deleted from codebase.

---

### F23 Inventory Layout Framework

**Status:** Prototype · **Adoption:** 1/11 (Bridge)

**Purpose:** Persists and restores per-player hotbar layout preferences per gamemode.

**Key interface:** `InventoryLayoutAware` — `inventoryLayoutGamemodeId()`, `inventoryLayoutProfileId()`

---

## 5. Confirmed Bugs

These bugs are confirmed from source code review. They must be fixed before any
framework migration work that depends on the affected component.

| ID | Component | Description | Fix |
|----|-----------|-------------|-----|
| **B01** | `SpectatorService.onPlayerLeave` | Does not call `stopSpectating` for the leaving player's own session. The method removes the player from other sessions' target lists (correct) but leaves the leaving player's own `SpectatorSession` in `this.sessions` (leak). | Add `this.stopSpectating(player.getUuid(), SpectatorStopReason.PLAYER_LEFT)` in `onPlayerLeave` after updating other sessions. |
| **B02** | `MurderMysteryMinigame.configureGameRules` and `DuelsMinigame.configureGameRules` | Both return `defaults(true, true)` (`doImmediateRespawn = true`) while using `DeathLifecycleManager` with `interceptsRespawn = true`. This causes a race: client auto-respawns at the same tick the framework tries to transition the player to SPECTATOR. | Change both to `defaults(true, false)`. See I01. |
| **B03** | `BlockShuffleMinigame.initialize()` | Calls `SpectatorService.getInstance().clearAll()` explicitly. `MinigameManager.setActiveMinigame()` already calls this before `initialize()`. Double-clear is currently harmless but signals incorrect ownership of framework cleanup. | Remove the `clearAll()` call from `BlockShuffleMinigame.initialize()`. |
| **B04** | `BlockShuffleMinigame` (and `DeathShuffleMinigame`) | No `onPlayerLeave` implementation. Disconnected players remain in `activePlayers` indefinitely, which blocks win-condition evaluation. | Implement `PlayerLeaveAware.onPlayerLeave()` in both gamemodes. |

---

## 6. Known Framework Interactions

### I01 Death Lifecycle vs. doImmediateRespawn

**Rule:** Any gamemode implementing `DeathAwareMinigame` with a `DeathPolicy` where
`interceptsRespawn()` returns `true` MUST manually apply `doImmediateRespawn = false` in
`initialize()`.

**Why:** When `doImmediateRespawn = true`, the Minecraft client automatically sends a
respawn packet without showing the death screen. The Death Lifecycle Framework calls
`changeGameMode(SPECTATOR)` before this happens — relying on the client still being
on the death screen. If the client auto-respawns at the same tick, the player may
briefly enter SURVIVAL at world spawn before the framework's spectator transition
takes effect. This is a client-server race condition.

**Affected gamemodes right now:** Duels (B02), MurderMystery (B02).

**Summary table:**

| Uses Death Framework? | interceptsRespawn | doImmediateRespawn | Result |
|-----------------------|:-----------------:|:------------------:|--------|
| Yes | true | false ✅ | Correct — framework handles respawn |
| Yes | true | true ❌ | Race condition — B02 |
| Yes | false | true | Vanilla death screen shown; player clicks to respawn |
| No | N/A | true | Vanilla immediate respawn — correct for gamemodes without framework |

---

### I02 TeamManager vs. VanillaTeamAdapter

Logical teams (`TeamManager`) are always authoritative. `VanillaTeamAdapter` is a
display-only mirror. `ChatRouter` reads from `TeamManagerProvider`, not from the
Minecraft scoreboard. Never bypass `TeamManager` to set scoreboard team membership
directly.

---

### I03 FrameworkModule Auto-Cleanup

Any object registered via `AbstractMinigame.getOrRegisterModule(module)` will have
`module.cleanup(server)` called automatically when `stopGame()` runs. This covers
`ScoreboardTemplate`, `RoleManager`, `CorpseManager`. Do not manually call `cleanup()`
on these — it will run twice.

---

## 7. Adding a New Framework

When you add a new framework to the codebase, also:

1. Add an entry to **this file** under Section 4 using the template below.
2. Add a row to the matrix in **GAMEMODE_STATUS.md**.
3. Add a decision entry in **DECISIONS.md** explaining why the framework was introduced.
4. Add migration steps in **MIGRATION_PLAN.md** if existing gamemodes need to adopt it.

**New framework entry template:**

```markdown
### FXX Framework Name

**Status:** [Prototype | Partially Implemented | Mostly Implemented | Fully Implemented]
**Adoption:** X/11

**Purpose:** One paragraph — what problem does it solve.

**Key classes:**
| Class | Role |
|-------|------|
| `ClassName` | Role |

**Lifecycle:** Brief description of the start→tick→end flow.

**To adopt:** What a gamemode must do to use this framework.

> **AI note:** Any critical gotchas or invariants for AI-assisted sessions.
```
