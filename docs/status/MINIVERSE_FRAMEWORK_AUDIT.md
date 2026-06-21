# Miniverse Framework Audit Report

**Audit Date:** June 2026  
**Repository:** `github.com/Fr0sT-x/miniverse`  
**Branch:** `master`  
**Mod Platform:** Fabric 1.21.1 / Java 21  

---

## 1. Executive Summary

| Metric | Value |
|--------|-------|
| Total Frameworks Discovered | 23 |
| Total Gamemodes | 11 |
| All gamemodes extend `AbstractMinigame` | Yes |
| Death Lifecycle Framework adoption | 2/11 (18%) |
| Persistent Session Framework adoption | 5/11 (45%) |
| Team Framework adoption | 6/11 (55%) |
| Arena Framework adoption | 1/11 (9%) |

**Overall Assessment:**  
Miniverse has a strong, well-designed framework core. `AbstractMinigame` provides universal coverage of ~15 cross-cutting concerns to all 11 gamemodes simultaneously, which is architecturally excellent. The Session Framework, Match Lifecycle, Freeze, Spectator, Map Protection, and Global Match Rules frameworks are all effectively universal. The primary gaps are in the **Death Lifecycle Framework** (only 2 gamemodes migrated), **Arena Framework** (only Duels), and the **Player Snapshot / Persistence Framework** (partial). `GameState` has redundant values (e.g. `RUNNING`, `IN_PROGRESS`, `PLAYING` all mean "active") which creates cross-gamemode state inconsistency. Several singletons remain that the project has already flagged for removal.

**Top Strengths:**
- `AbstractMinigame` is a powerful, well-constructed base that eliminates enormous amounts of boilerplate
- `FreezeService`, `SpectatorService`, `RegionTriggerService`, `ProtectedItemService` are all clean singletons with single responsibilities
- `DeathLifecycleManager` is architecturally sound and properly wired in `MinigameEventRouter`
- `GlobalMatchRules` centralizes gamerule configuration cleanly
- `TeamManager` + `VanillaTeamAdapter` is a clean separation of game-logic teams from display teams

**Top Weaknesses:**
- `GameState` enum is bloated with overlapping values; no single canonical "running" state
- Death Lifecycle Framework adoption is very low (2/11)
- `MinigameManager` is still a singleton despite a `// TODO: Migrate` comment
- `SpectatorService.onPlayerLeave` does not clean up the *leaving player's own session* (confirmed bug)
- `MinigameEventRouter.onAfterDeath` falls through to `active.onPlayerDeath(player)` — **not** to `DeathAwareMinigame` dispatch — when the manager is null (confirmed bug; check code path carefully)
- Arena Framework is isolated to Duels with no generalisation path visible

---

## 2. Framework Inventory

---

### F01 — Session Framework

**Purpose:** Manages the full lifecycle of a backend server session: creation, boot, player assignment, routing, state transitions (CREATED → LAUNCHING → RUNNING → STOPPING → STOPPED), and recovery on crash/restart.

**Scope:** Lobby-side orchestration. All gamemodes depend on this indirectly (the session is how their backend process is spawned and players are routed to it).

**Core Components:**
- `SessionRegistry` — disk-based session store, cleanup, snapshots
- `SessionRoutingEvents` — Fabric event hooks for join/tick/disconnect; proactive missed-transfer retry
- `PlayerTransferService` — sends players to backend via Velocity or direct TCP
- `SessionRuntimeConfig` — reads `session.properties`/`session.json` for launch context
- `GameSession`, `SessionGroup` — session entity model
- `SessionBootstrapper` — backend-side; receives join events, ticks session start, invokes game `Handler`
- `SessionRecoveryService` — restores sessions from disk after server crash

**Lifecycle:** Lobby spawns backend → session enters LAUNCHING → backend boots → players route → session enters RUNNING → game ends → session enters STOPPING → backend shuts down → STOPPED

**Responsibilities:** Session CRUD, player transfer coordination, backend process tracking, crash recovery, late-join routing

**Dependencies:** Velocity proxy (optional), `SessionRuntimeConfig`, `TransitionTransferCoordinator`

**Current Status:** Mostly Implemented

**Notes:** Two confirmed bugs: (1) `PlayerTransferService.transferAssignedPlayers` is fire-and-forget with no retry — players who disconnect during backend boot are silently dropped. (2) `GameSession.withoutMember`/`addMemberToGroup` copy `BackendInstance` state by value, which can freeze a group permanently in LAUNCHING if a rebuild races with `markRunning`.

---

### F02 — Match Lifecycle Framework

**Purpose:** Controls the pre-match countdown (WAITING → STARTING → RUNNING), end-of-match return sequence (RUNNING → ENDING → RETURNING → STOPPED), admin pause/resume, and broadcasting of start/end titles.

**Scope:** All gamemodes that use `SessionBootstrapper` and `AbstractMinigame`.

**Core Components:**
- `MatchLifecycleController` — drives countdown, tracks lifecycle players, manages return flow
- `MatchLifecycleOptions` — configures countdown time, titles, and display options
- `MatchEndResult` — encodes match outcome for display
- `MatchProgressionValidator` (interface on `Minigame`) — lets gamemodes block progression (e.g. insufficient players)
- `MatchLifecycleCommands` — admin commands (`/match start`, `/match end`, etc.)
- `StandardEndSequence` — convenience helper to trigger end from within a gamemode

**Lifecycle:** `beginMatch()` → STARTING → tick countdown → `startCallback.run()` → RUNNING → `endMatch()` → ENDING → return timer → players transferred back

**Responsibilities:** Countdown timing, start/end broadcasting, return-to-lobby transfer, pause state management

**Dependencies:** `FreezeService`, `SessionRegistry`, `TransitionTransferCoordinator`, `ChatRouter`

**Current Status:** Fully Implemented

**Notes:** Universally used by all 11 gamemodes via `AbstractMinigame`. Lifecycle players are snapshotted at match start (for return routing), which is correct.

---

### F03 — Freeze Framework

**Purpose:** Freezes players in place (prevents input) by sending a custom client packet. Multi-reason aware — a player stays frozen while any reason is still active.

**Scope:** Universal. Used by match start countdown, admin pause, and spectator mechanics.

**Core Components:**
- `FreezeService` (singleton) — reason-keyed freeze map, packet sender
- `FreezeReason` (enum) — `MATCH_START`, `ADMIN_PAUSE`, `SPECTATING`, etc.
- Client-side mixin — processes `FreezeStatePayload` and blocks input

**Lifecycle:** `freeze(player, reason)` → add reason → if first reason, send freeze packet → `unfreeze(player, reason)` → remove reason → if no reasons left, send unfreeze packet

**Responsibilities:** Freeze state tracking, reason management, packet broadcast

**Dependencies:** `NetworkConstants.FreezeStatePayload`, Fabric networking

**Current Status:** Fully Implemented

**Notes:** Very clean, single-responsibility design. `onPlayerLeave` correctly removes players. This is one of the best-designed services in the codebase.

---

### F04 — Spectator Framework

**Purpose:** Manages players who are watching a game in spectator mode, including camera control, target selection, policy enforcement (who they can spectate), and clean session cleanup.

**Scope:** All gamemodes that put dead/eliminated players into spectate mode.

**Core Components:**
- `SpectatorService` (singleton) — session CRUD, tick-driven validation, camera updates, player-leave cleanup
- `SpectatorSession` — per-player spectator state (policy, target provider, mode, return mode)
- `SpectatorPolicy` / `SpectatorPolicies` — pluggable policy (unrestricted, locked, team-only, fixed-target)
- `SpectatorTargetProvider` / `SpectatorTargetProviders` — pluggable target list (roster, online, none, filtered)
- `SpectatorMode` — STANDARD, ELIMINATED, ADMIN
- `SpectatorCameraController` — handles camera entity attachment
- `SpectatorEvents` — listener event bus (start, stop, target changed, no-target elimination)

**Lifecycle:** `startSpectating()` → create session → switch to spectator gamemode → attach camera → tick validates targets → `stopSpectating()` → switch back to return mode

**Responsibilities:** Spectator mode transitions, camera management, target validation, session cleanup, policy enforcement

**Dependencies:** `FreezeService`, `MinigameManager`, Fabric events

**Current Status:** Mostly Implemented

**Notes:** **Confirmed bug:** `SpectatorService.onPlayerLeave(player)` does not call `stopSpectating` for the leaving player's own session. The method dispatches to other sessions that *target* the leaving player (correct), but the leaving player's own `SpectatorSession` is not removed from `this.sessions`. This leaks the session and will cause issues if that player reconnects.

---

### F05 — Death Lifecycle Framework

**Purpose:** Centralises the entire death flow: fatal damage interception → inventory/effects cleanup → spectator transition → post-death timer or condition → respawn location resolution → respawn execution. Eliminates per-gamemode death handling duplication.

**Scope:** Opt-in. Only gamemodes implementing `DeathAwareMinigame` and holding a `DeathLifecycleManager` instance use it.

**Core Components:**
- `DeathLifecycleManager` — orchestrator; holds per-player state machines
- `DeathContext` (record) — immutable snapshot of death circumstances
- `DeathState` (enum) — `ALIVE`, `DEATH_PROCESSING`, `SPECTATING`, `RESPAWNING`, `DISCONNECTED`
- `PlayerDeathStateMachine` — guards valid state transitions
- `DeathLifecycleConfig` (interface) — gamemode supplies policies and callbacks
- `DeathPolicy` — immediate death processing (cleanup, intercept respawn)
- `DeathSpectatorPolicy` — spectator mode configuration after death
- `PostDeathPolicy` — timer or condition before respawn
- `RespawnStrategy` — resolves respawn location
- `DeathLifecycleCallbacks` — optional event hooks (onDeath, onRespawnComplete, etc.)
- `DeathAwareMinigame` (interface) — opt-in contract exposing `getDeathLifecycleManager()`
- `NoTargetPolicy` — behaviour when spectator has no valid targets

**Lifecycle:** `handleFatalDamage()` → `DEATH_PROCESSING` → `DeathPolicy.execute()` → `SPECTATING` → `DeathSpectatorPolicy` configures spectator → `PostDeathPolicy.start()` → (tick/condition) → `RESPAWNING` → `RespawnStrategy.resolve()` → teleport + gamemode change → `ALIVE`

**Responsibilities:** Death interception, spectator setup after death, respawn timing and location, state machine consistency, callback dispatch

**Dependencies:** `SpectatorService`, `PlayerDeathStateMachine`, `DeathLifecycleConfig`

**Current Status:** Mostly Implemented

**Notes:** **Confirmed bug:** `MinigameEventRouter.onAfterDeath` dispatches to `DeathAwareMinigame` correctly *but only when `getDeathLifecycleManager()` returns non-null*. Currently `MurderMysteryMinigame` and `DuelsMinigame` both initialize `deathLifecycleManager` in `onMatchStart()` but the field is `null` before `startGame()` is called. If a player somehow dies before the game starts, the framework falls through to `active.onPlayerDeath(player)` (the no-op default on `AbstractMinigame`). This is acceptable pre-game, but the null check should be explicitly documented. The bigger confirmed bug (from previous audit) is that `onAfterDeath` was *never* dispatching to `DeathAwareMinigame` at all in an earlier version — this now appears to be fixed.

---

### F06 — Persistent Session Framework

**Purpose:** Saves and restores the state of a running minigame session to disk, enabling crash recovery, admin pause persistence, and server restarts without losing game progress.

**Scope:** Opt-in via `PersistentMinigame`. `AbstractMinigame` provides no-op defaults so all gamemodes silently inherit the interface but only those that override `saveRuntimeState()` / `loadRuntimeState()` are truly persistent.

**Core Components:**
- `PersistentMinigame` (interface) — `saveRuntimeState()`, `loadRuntimeState()`, `saveSessionData()`, `loadSessionData()`
- `MinigameSessionStore` — autosave timer, save/load orchestrator, backup file management
- `MinigameSessionManager` — in-memory session map, serializer registry
- `PlayerStateStore` / `PlayerStateSnapshot` — captures and restores full player state (position, inventory, effects, gamemode)
- `SessionData` / `JsonSessionData` — session data model
- `SessionRecoveryService` — on-boot recovery; scans disk, restores active session

**Lifecycle:** `MinigameSessionStore.save()` every 30s + on pause + on end → `SessionRecoveryService.recoverUnfinishedSessions()` on server start → `loadSessionData()` on active gamemode

**Responsibilities:** State serialisation/deserialisation, autosave, crash recovery, pause persistence

**Dependencies:** `MinigameRuntime`, `SessionRuntimeConfig`, Gson, file I/O

**Current Status:** Partially Implemented

**Notes:** `AbstractMinigame.saveRuntimeState()` returns an empty `JsonObject`, meaning gamemodes that don't override this get silently saved as empty JSON. Only gamemodes with meaningful state (Manhunt, BountyHunt, DeathSwap, BlockShuffle, DeathShuffle) actually override these methods. Bridge and Infection implement `PersistentMinigame` explicitly but their save fidelity is unclear without reading the full file.

---

### F07 — Global Match Rules Framework

**Purpose:** Standardises gamerule application at match start (keepInventory, pvpEnabled, doDaylightCycle, doWeatherCycle, fallDamage, naturalRegeneration, announceAdvancements, doImmediateRespawn) and resets them at match end.

**Scope:** Universal — `AbstractMinigame.startGame()` calls `configureGameRules()` (abstract) and applies the result; `stopGame()` calls `resetGameRules()` to restore defaults.

**Core Components:**
- `GlobalMatchRules` (record) — immutable gamerule configuration
- `AbstractMinigame.configureGameRules()` — abstract method gamemodes must implement
- `AbstractMinigame.applyGameRulesOverrides()` — per-property overrides from session config
- `GlobalMatchRules.apply(server)` — applies to all worlds
- `GlobalMatchRules.defaults(keepInventory, immediateRespawn)` — convenience constructor

**Lifecycle:** Game start → `configureGameRules()` + overrides merged → `apply(server)` → game end → `GlobalMatchRules.defaults(false, false).apply(server)`

**Responsibilities:** Consistent gamerule application and restoration

**Dependencies:** `MinecraftServer.getWorlds()`, `GameRules`

**Current Status:** Fully Implemented

**Notes:** All 11 gamemodes implement `configureGameRules()` (it's abstract in `AbstractMinigame`). Very clean design.

---

### F08 — Team Framework

**Purpose:** Manages logical game teams (not Minecraft scoreboard teams) — player assignment, membership lookup, team metadata, and snapshots for vanilla sync.

**Scope:** Optional. Only gamemodes with team-based play use it.

**Core Components:**
- `TeamManager` — team CRUD, player-to-team mapping, snapshots
- `GameTeam` — team entity with member list
- `TeamMembership` — player-team association record
- `TeamRole` — role within a team (MEMBER, CAPTAIN, etc.)
- `TeamManagerProvider` (interface) — gamemodes expose their `TeamManager` via this
- `TeamColorPalette` — deterministic color assignment
- `TeamSnapshot` — immutable view of team state for vanilla sync
- `VanillaTeamAdapter` — mirrors logical teams into Minecraft scoreboard teams (for nametag color, friendly fire rules)
- `VanillaTeamDescriptor` / `VanillaTeamOptions` — configuration for vanilla team sync
- `ChatRouter` — reads `TeamManagerProvider` to route team chat

**Lifecycle:** Gamemode creates `TeamManager`, assigns players → `syncVanillaTeams()` called from `AbstractMinigame` on start and when teams change → `clearVanillaTeams()` on stop

**Responsibilities:** Logical team membership, vanilla scoreboard mirroring, team chat routing

**Dependencies:** `VanillaTeamAdapter`, `ChatRouter`, Minecraft scoreboard API

**Current Status:** Fully Implemented

**Notes:** Excellent separation of concerns. Logical team data is authoritative; scoreboard teams are purely display. `ChatRouter` correctly queries `TeamManagerProvider` rather than scoreboard teams.

---

### F09 — Map Protection Framework

**Purpose:** Protects pre-existing map blocks from being broken during a game session. Only blocks placed dynamically during the session (tracked in `MapProtectionTracker`) are breakable.

**Scope:** Applied to all gamemodes via `MinigameEventRouter` block-break hooks. Bypassed in `MAP_EDITOR` mode.

**Core Components:**
- `MapProtectionManager` — static utility; `isProtected(world, pos)`, `canBreak(player, pos, sendMessage)`
- `MapProtectionTracker` (per-runtime) — set of dynamically placed block positions
- `MinigameContext.protectionTracker()` — exposes the tracker per-runtime
- `MinigameEventRouter` — `PlayerBlockBreakEvents` hook checks protection; `UseBlockCallback` records placed blocks

**Lifecycle:** Player places block → tracked in `MapProtectionTracker` → player breaks block → `isProtected()` checks tracker → if not in tracker, block is protected → send error message

**Responsibilities:** Map block protection, placed-block tracking, bypass for OP/creative/editor

**Dependencies:** `MinigameManager`, `SessionRuntimeConfig`, `BackendLaunchMode`, `MapGamemodeRegistry`

**Current Status:** Fully Implemented

**Notes:** Correctly skips protection for `MAP_EDITOR` mode and for gamemodes not in `MapGamemodeRegistry`. Clean static utility design.

---

### F10 — Region Trigger Framework

**Purpose:** Fires `PlayerRegionAware.onPlayerEnterRegion` / `onPlayerExitRegion` callbacks when players move in or out of named map marker regions. Used by Bridge for goal detection.

**Scope:** Optional opt-in via `PlayerRegionAware`. Only gamemodes implementing this interface receive callbacks.

**Core Components:**
- `RegionTriggerService` (singleton) — tick-driven, per-player region tracking, enter/exit dispatch
- `RegionRestrictionService` (singleton) — queries `RuntimeMarkerCache` for restriction markers (e.g. `BUILD_DENIED`)
- `PlayerRegionAware` (interface) — `onPlayerEnterRegion`, `onPlayerExitRegion`
- `RuntimeMarkerCache` — tick-refreshed cache of active map markers

**Lifecycle:** `RegionTriggerService.tick()` → foreach live participant → compare current regions vs. previous → fire enter/exit → update per-player map

**Responsibilities:** Region enter/exit detection, restriction querying, marker cache management

**Dependencies:** `MinigameManager`, `RuntimeMarkerCache`, `MapEditorMarkerStore`

**Current Status:** Fully Implemented

**Notes:** Only Bridge currently implements `PlayerRegionAware`. The framework is ready for other gamemodes.

---

### F11 — Map Editor Framework

**Purpose:** Provides a structured in-game map editor for placing and configuring named markers (spawn points, regions, goal zones). Each gamemode registers its `MapEditorExtension` describing required/optional markers.

**Scope:** Editor mode only. Does not affect runtime gameplay.

**Core Components:**
- `MapEditorExtension` — per-gamemode marker schema (marker definitions, validators)
- `MarkerDefinition` — schema for a single marker type (key, display name, marker type, counts, triggers)
- `MapEditorExtensionRegistry` — registry of all gamemode editor extensions
- `MapEditorMarkerStore` — persists placed markers to disk
- `MapEditorCommands` / `MapEditorEvents` — in-game commands and placement events
- `MapEditorNetwork` / `MapEditorPlacementController` — client-server communication for editor UI
- `MapEditorValidator` — per-gamemode validation of marker configuration
- `MapEditorEvents` — auto-shuts-down backend if empty for >60s in editor mode

**Lifecycle:** Session launched in `MAP_EDITOR` mode → player joins → teleported to editor spawn → places markers via UI/commands → markers saved → validator checks completeness → backend shuts down

**Responsibilities:** Marker CRUD, gamemode schema registration, map validation, editor UI networking

**Dependencies:** `SessionRuntimeConfig`, `BackendLaunchMode`, `MapStore`, `MapDescriptor`

**Current Status:** Mostly Implemented

**Notes:** Well-designed schema-driven system. The framework is solid; completeness of each gamemode's editor extension registration is harder to verify without reading all definition files.

---

### F12 — Scoreboard Framework

**Purpose:** Provides a reusable sidebar scoreboard with updateable lines, player-specific show/hide, and auto-cleanup.

**Scope:** Optional — gamemodes instantiate `ScoreboardTemplate` and add `ScoreboardLine`s. `AbstractMinigame` automatically cleans up `FrameworkModule` instances (including scoreboards) on `stopGame()`.

**Core Components:**
- `ScoreboardTemplate` implements `FrameworkModule` — creates/manages a scoreboard objective, tracks viewers
- `ScoreboardLine` — a single updateable row
- `AbstractMinigame.getOrRegisterModule()` — lazy module registration with auto-cleanup

**Lifecycle:** Gamemode calls `new ScoreboardTemplate(...)` → `addLine(text)` → `show(player)` → `line.update(text)` per tick → `cleanup()` called on match end

**Responsibilities:** Scoreboard lifecycle, per-player show/hide, line update batching

**Dependencies:** Minecraft scoreboard API, `FrameworkModule`

**Current Status:** Fully Implemented

**Notes:** Used by most gamemodes. `AbstractMinigame.getOrRegisterModule()` ensures scoreboard modules are cleaned up without gamemodes needing to do it manually.

---

### F13 — Protected Items Framework

**Purpose:** Prevents certain items (e.g. tracker compasses, bounty items) from being dropped, deleted, or held by ineligible players. Enforces item rules by type tag.

**Scope:** Optional. Gamemodes register `ProtectedItemRule`s at start and clear them at end.

**Core Components:**
- `ProtectedItemService` (singleton) — rule registry, tick-based enforcement, drop/deletion cancellation
- `ProtectedItemRule` — rule definition (type, canHold predicate, preventDrop, preventDeletion)
- `ProtectedItemTags` / `ProtectedItemTypes` — item tag/type constants

**Lifecycle:** Gamemode calls `registerRule()` at match start → `MinigameEventRouter` calls `tick()` and `onPlayerRespawn()` → drops/slot moves are cancelled → `clearRules()` at match end

**Responsibilities:** Item drop/deletion prevention, eligibility enforcement, tick-based reclamation

**Dependencies:** `MinigameEventRouter`, Fabric item events

**Current Status:** Fully Implemented

**Notes:** Used by Manhunt and BountyHunt (tracker compasses). The framework is ready for other gamemodes.

---

### F14 — Kit Framework

**Purpose:** Defines and applies pre-configured player loadouts (armor, inventory, offhand, effects) identified by `Identifier`. Supports custom kit loading from JSON config files.

**Scope:** Optional opt-in. Currently only Duels actively uses kit selection.

**Core Components:**
- `Kit` — loadout definition (armor, inventory, offhand, effects)
- `KitRegistry` — static `Identifier`-keyed registry
- `Kit.apply(player)` — applies loadout to a player
- `KitRegistry.loadCustomKits(server)` — loads kits from `miniverse/custom_kits/*.json` on server start

**Lifecycle:** Server start → `KitRegistry.loadCustomKits()` → kits registered → game start → kit selected by ID → `apply()` called on player

**Responsibilities:** Kit registration, application, serialisation/deserialisation

**Dependencies:** `KitRegistry`, Fabric loader for config path

**Current Status:** Mostly Implemented

**Notes:** Solid foundation. Only Duels uses it actively. Bridge and Infection give players items manually rather than using the Kit framework.

---

### F15 — Role Framework

**Purpose:** Assigns and manages gameplay roles per player within a game (e.g. Murderer, Detective, Innocent in Murder Mystery). Roles carry lifecycle hooks (`onAssign`, `onRemove`).

**Scope:** Optional opt-in. Currently only Murder Mystery uses it.

**Core Components:**
- `RoleManager` implements `FrameworkModule` — per-player role map, assign/remove with lifecycle hooks
- `Role` (interface) — `onAssign(player)`, `onRemove(player)`, `isSpectator()`
- `AbstractMinigame.roles()` — lazy accessor with auto-cleanup

**Lifecycle:** `RoleManager.assignRole(player, role)` → `role.onAssign(player)` → during game, `hasRole(player, RoleClass)` used for logic → on stop, `cleanup()` → `role.onRemove(player)` for all

**Responsibilities:** Role assignment, role-based predicate queries, lifecycle hooks

**Dependencies:** `FrameworkModule`, `VisibilityManager`

**Current Status:** Partially Implemented

**Notes:** Only Murder Mystery uses the Role Framework. It is generic enough to apply to Infection (survivor/infected), Bridge (team roles), and Manhunt (hunter/speedrunner). Those gamemodes manage roles via direct team-membership checks rather than the `RoleManager`.

---

### F16 — Visibility Framework

**Purpose:** Controls player name-tag visibility rules between roles (e.g. spectators are hidden from active players, murderers are visible to detectives differently). Built on `VanillaTeamAdapter`.

**Scope:** Optional. Currently only Murder Mystery uses it.

**Core Components:**
- `VisibilityManager` — wraps `VanillaTeamAdapter` to apply role-based visibility; separates active and spectator teams
- `VanillaTeamAdapter` (shared with Team Framework)

**Lifecycle:** After role assignment → `VisibilityManager.sync(server)` → updates vanilla team membership → name-tag visibility applied client-side

**Responsibilities:** Name-tag visibility management, role-to-display-team mapping

**Dependencies:** `RoleManager`, `VanillaTeamAdapter`

**Current Status:** Partially Implemented

**Notes:** Only Murder Mystery currently uses this. Infection has similar "survivors can't see infected names" requirements that are handled with raw Minecraft name-tag packets instead.

---

### F17 — Corpse Framework

**Purpose:** Spawns a visual "corpse" (an armor stand) at the location where a player died.

**Scope:** Optional. Currently only Murder Mystery uses it.

**Core Components:**
- `CorpseManager` implements `FrameworkModule` — spawns/tracks/cleans up corpse armor stands
- `AbstractMinigame.corpses()` — lazy accessor with auto-cleanup

**Lifecycle:** Player dies → `corpses().spawnCorpse(player)` → armor stand spawned at death position → game ends → `cleanup()` removes all armor stands

**Responsibilities:** Corpse spawning, cleanup

**Dependencies:** `FrameworkModule`, `ArmorStandEntity`

**Current Status:** Partially Implemented

**Notes:** Functional but minimal. The corpse is a plain armor stand with the player's name. A full corpse system would include player skin rendering (e.g. via a client-side rendering approach). Only Murder Mystery uses it.

---

### F18 — Arena Framework

**Purpose:** Manages physical arena instances on a backend server — registering arena regions, resetting block state after each match/round, and tracking which arena a player is in.

**Scope:** Currently only Duels uses it.

**Core Components:**
- `ArenaManager` — arena registry, idle/busy state, block change tracking, block reset
- `Arena` — individual arena instance with state machine (`IDLE`, `RUNNING`, `RESETTING`)
- `ArenaRegion` — bounding box definition for an arena
- `ArenaState` — arena lifecycle state

**Lifecycle:** Game start → `ArenaManager` registered with world → `findIdleArena()` for each duel match → `Arena.start()` → block changes tracked → round ends → `Arena.reset()` → `IDLE`

**Responsibilities:** Arena instance management, block state restoration, player-to-arena mapping

**Dependencies:** `ServerWorld`, block state tracking

**Current Status:** Partially Implemented

**Notes:** Well-designed. Only Duels uses it, but the API is generic enough for Bridge (which has its own field-reset logic), Infection spawn arenas, or any other gamemode with resettable spaces.

---

### F19 — Countdown Service Framework

**Purpose:** Tracks which countdown announcements have been sent (to prevent duplicates) and provides helpers for visible countdown titles.

**Scope:** Optional utility. Only DeathSwap explicitly uses it.

**Core Components:**
- `CountdownService` — `announceOnce()`, `announceVisibleCountdown()` with deduplication set
- `CountdownService.reset()` — clears the announcement set

**Lifecycle:** Called from `onGameTick()` every tick → checks if countdown second has been announced → fires title/sound → records in set

**Responsibilities:** Countdown deduplication, title/subtitle display, sound playback

**Dependencies:** None (stateless except for announcement set)

**Current Status:** Fully Implemented

**Notes:** Simple, clean utility. Many gamemodes implement their own countdown announce deduplication (`announcedGraceThresholds`, `timeWarningsShown` etc.) rather than using this service, creating duplication.

---

### F20 — Player Snapshot Framework

**Purpose:** Captures and restores full player state (position, gamemode, inventory, effects, hunger, experience, fire ticks) for crash recovery and session restoration.

**Scope:** Used by `MinigameSessionStore` when saving persistent sessions. Not called per-tick.

**Core Components:**
- `PlayerStateSnapshot` (record) — full player state snapshot
- `PlayerStateStore` — captures all online participants, merges with previous snapshots for offline players

**Lifecycle:** `MinigameSessionStore.save()` → `PlayerStateStore.capture(runtime)` → JSON serialised → saved to disk → on recovery, snapshots restored

**Responsibilities:** Player state serialisation, offline player preservation

**Dependencies:** `MinigameRuntime`, `SessionRoster`, NBT serialisation

**Current Status:** Mostly Implemented

**Notes:** Correct design but restoration is only triggered if the gamemode implements `PersistentMinigame` meaningfully. Gamemodes that inherit the no-op `AbstractMinigame.saveRuntimeState()` don't benefit.

---

### F21 — Derangement / Swap Framework

**Purpose:** Provides a shuffle algorithm that guarantees no player is assigned to themselves (derangement), with penalty scoring to avoid recent repeat pairings.

**Scope:** Currently only DeathSwap uses it.

**Core Components:**
- `DerangementAssignment<T>` — produces derangement maps from collections; supports recent-target avoidance

**Lifecycle:** Called before each swap in DeathSwap → returns `Map<UUID, UUID>` of swap assignments

**Responsibilities:** Derangement generation, recent-target penalty

**Dependencies:** None

**Current Status:** Fully Implemented

**Notes:** Generic and reusable. Only DeathSwap uses it. Could be used in team shuffle scenarios.

---

### F22 — Respawn Policy Framework

**Purpose:** Abstracts the post-death player state: either eliminated (spectator forever) or lives-based (return to survival). Drives the spectator transition after death in DeathSwap.

**Scope:** Currently only DeathSwap uses `RespawnPolicyController` directly.

**Core Components:**
- `RespawnPolicyController` — `handleDeath(player)`, `handleRespawn(player)` with `RespawnMode`
- `RespawnMode` (enum) — `ELIMINATION`, `POINTS`

**Lifecycle:** Player dies → `handleDeath(player)` → if ELIMINATION, puts player into spectator mode → player respawn event → `handleRespawn(player)` → if ELIMINATION, keeps in spectator; if POINTS, returns to survival

**Responsibilities:** Post-death gamemode management (survival vs. spectator)

**Dependencies:** `SpectatorService`

**Current Status:** Partially Implemented

**Notes:** Predates the Death Lifecycle Framework and overlaps with it significantly. `RespawnPolicyController` is a simpler version of what `DeathLifecycleManager` does. DeathSwap should eventually migrate to the Death Lifecycle Framework; `RespawnPolicyController` would then be retired or wrapped.

---

### F23 — Inventory Layout Framework

**Purpose:** Persists and restores per-player hotbar layout preferences per gamemode.

**Scope:** Currently only Bridge uses it.

**Core Components:**
- `InventoryLayoutAware` (interface) — `inventoryLayoutGamemodeId()`, `inventoryLayoutProfileId()`

**Lifecycle:** Player joins session → layout loaded for `(gamemodeId, profileId)` → applied to hotbar → player customises → saved on leave

**Responsibilities:** Hotbar layout persistence, per-gamemode/profile storage

**Dependencies:** File I/O (layout data store not fully visible in audit scope)

**Current Status:** Prototype

**Notes:** Only Bridge implements this. The full storage mechanism was not fully inspected. If it involves per-player files, it could conflict with the `PlayerStateStore` approach.

---

## 3. Gamemode Framework Matrix

*Note: All gamemodes extend `AbstractMinigame`, which implements `RuntimeContextAware`, `ServerTickAware`, `ItemUseAware`, `PlayerDamageAware`, `EntityDeathAware`, `PlayerJoinAware`, `PlayerLeaveAware`, `PlayerRegionAware`, `PlayerRespawnAware`, `PersistentMinigame`, `PauseAwareMinigame`, and `DynamicParticipantMinigame`. These are marked ✅ for all gamemodes below since they are universally inherited. Gamemodes may override behaviour but all have these interfaces available.*

| Framework | Manhunt | Speedrun | Bounty Hunt | Death Swap | Resource Sprint | Block Shuffle | Death Shuffle | Duels | Murder Mystery | Bridge | Infection |
|-----------|---------|----------|-------------|------------|-----------------|---------------|---------------|-------|----------------|--------|-----------|
| Session (F01) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Match Lifecycle (F02) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Freeze (F03) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Spectator (F04) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Death Lifecycle (F05) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Persistent Session (F06) | ✅ | ❌ | ✅ | ✅ | ❌ | ⚠️ | ⚠️ | ❌ | ⚠️ | ✅ | ✅ |
| Global Match Rules (F07) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Team Framework (F08) | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ⚠️ | ❌ | ✅ | ✅ |
| Map Protection (F09) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Region Trigger (F10) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Map Editor (F11) | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| Scoreboard (F12) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| Protected Items (F13) | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Kit Framework (F14) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Role Framework (F15) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Visibility (F16) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Corpse (F17) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Arena (F18) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Countdown Service (F19) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Player Snapshot (F20) | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ⚠️ | ✅ | ✅ |
| Derangement/Swap (F21) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Respawn Policy (F22) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Inventory Layout (F23) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |

**Legend:** ✅ Fully Used · ⚠️ Partially Used · ❌ Not Used

---

## 4. Gamemode Compliance Reports

---

### Manhunt

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Team Framework (TeamManager + VanillaTeamAdapter), Protected Items (tracker compass), Scoreboard (sidebar timer), Player Snapshot (full persistence), `RuntimeContextAware`, `DynamicParticipantMinigame`, `PauseAwareMinigame`, `RosterAware`

**Frameworks Missing:** Death Lifecycle (F05), Arena (F18), Kit (F14), Role (F15), Region Trigger (F10)

**Partial Integrations:**
- `PersistentMinigame` — overrides `saveRuntimeState()`/`loadRuntimeState()` but uses a bespoke `ManhuntSpeedrunnerRespawnSystem` with its own tick loop, persistence, reconnect handling, and respawn protection that is **not** mapped through the Death Lifecycle Framework
- Spectator — used directly via `SpectatorService` inside `ManhuntSpeedrunnerRespawnSystem` rather than through the Death Lifecycle Framework
- Map Editor — likely has a registered extension but completeness is unverified

**Compliance Score:** 72/100

**Notes:** Manhunt has the most bespoke subsystems of any gamemode. `ManhuntSpeedrunnerRespawnSystem` is effectively a parallel death/respawn framework that predates `DeathLifecycleManager`. It is the correct gamemode to migrate **last** due to its reconnect handling, respawn protection timers, and persistence requirements. The tracker compass correctly uses `ProtectedItemService`. `RosterAware.onRosterChanged()` is implemented, showing forward-looking design.

---

### Speedrun

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Scoreboard, `DynamicParticipantMinigame`, `PauseAwareMinigame`, `PlayerRespawnAware`, `PlayerLeaveAware`, `EntityDeathAware`, `ServerTickAware`, VanillaTeamAdapter

**Frameworks Missing:** Death Lifecycle (F05), Team Framework (F08, no `TeamManager`), Protected Items (F13), Persistent Session (F06), Kit (F14), Role (F15), Arena (F18)

**Partial Integrations:**
- `PersistentMinigame` — inherited no-op from `AbstractMinigame`; no meaningful state saved
- Spectator — used directly for dead runner but not via Death Lifecycle Framework

**Compliance Score:** 58/100

**Notes:** Relatively simple gamemode. Single-player runner. No teams. Death handling is done inline in `EntityDeathAware.onEntityDeath()` by checking for `EnderDragonEntity`. This is correct for the win condition but the "runner dies → spectate" transition could migrate to Death Lifecycle. Low complexity makes it a reasonable second migration candidate.

---

### Bounty Hunt

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Scoreboard, Protected Items (tracker compass), Player Snapshot (persistence), `DynamicParticipantMinigame`, `RosterAware`, `PauseAwareMinigame`, `PlayerLeaveAware`, `PlayerDamageAware`, VanillaTeamAdapter, Protection Overlay (respawn/grace period rendering)

**Frameworks Missing:** Death Lifecycle (F05), Team Framework (F08, all-vs-all), Kit (F14), Role (F15), Arena (F18), Region Trigger (F10)

**Partial Integrations:**
- `PersistentMinigame` — implements `saveRuntimeState()` with meaningful state (scores, targets, invincibility)
- Death handling — done inline in `EntityDeathAware.onEntityDeath()` rather than Death Lifecycle

**Compliance Score:** 70/100

**Notes:** Well-implemented all-vs-all gamemode. Uses `ProtectionOverlaySender` for respawn/grace overlays, which is the correct visual framework. The tracker compass correctly uses `ProtectedItemService`. The `PlayerTracker` utility (F24 candidate below) is used here and in Manhunt, showing duplication.

---

### Death Swap

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator (directly), Global Match Rules, Map Protection, Scoreboard, Team Framework, Derangement/Swap (F21), Respawn Policy (F22), Countdown Service (F19), `PersistentMinigame` (full), `PlayerDamageAware`, `PlayerRespawnAware`, VanillaTeamAdapter, Player Snapshot

**Frameworks Missing:** Death Lifecycle (F05), Kit (F14), Role (F15), Arena (F18), Protected Items (F13), Region Trigger (F10)

**Partial Integrations:**
- `RespawnPolicyController` used instead of Death Lifecycle Framework — this is the pre-framework approach
- Spectator used directly for eliminated players rather than via Death Lifecycle

**Compliance Score:** 68/100

**Notes:** Has the most framework diversity of any gamemode. The `RespawnPolicyController` should be considered a migration candidate to Death Lifecycle Framework. `DerangementAssignment` usage is correct and well-placed. `CountdownService` usage is correct — this is the only gamemode that uses it, which means all other gamemodes are reimplementing countdown announce-deduplication manually.

---

### Resource Sprint

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Scoreboard, Team Framework (`TeamManager` + `TeamManagerProvider`), `PauseAwareMinigame`, `PlayerLeaveAware`, `PlayerRespawnAware`, VanillaTeamAdapter

**Frameworks Missing:** Death Lifecycle (F05), Persistent Session (F06, no override), Kit (F14), Role (F15), Arena (F18), Protected Items (F13), Region Trigger (F10), Countdown Service (F19)

**Partial Integrations:**
- `PersistentMinigame` — inherited no-op; no meaningful state saved (not critical — Resource Sprint is not crash-recovery critical)
- Time warnings reimplemented manually (see `timeWarningsShown` set)

**Compliance Score:** 62/100

**Notes:** Solid, clean gamemode. Primarily item-collection objective. Team-based. No PvP death handling required. The main gap is the manual countdown-warning deduplication pattern (should use `CountdownService`).

---

### Block Shuffle

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator (directly for eliminated players), Global Match Rules, Map Protection, Scoreboard, `DynamicParticipantMinigame`, `PauseAwareMinigame`

**Frameworks Missing:** Death Lifecycle (F05), Team Framework (F08, all vs all), Persistent Session (F06 partial), Kit (F14), Role (F15), Arena (F18), Protected Items (F13), Region Trigger (F10), Countdown Service (F19), `PlayerLeaveAware` (not implemented despite `AbstractMinigame` default)

**Partial Integrations:**
- `PersistentMinigame` — inherited but no meaningful `saveRuntimeState()` override found; round state and `activePlayers`/`points` are not persisted
- Spectator — called directly `SpectatorService.getInstance().clearAll()` in `initialize()`, bypassing framework patterns

**Compliance Score:** 54/100

**Notes:** Block Shuffle is relatively straightforward but has the weakest framework integration. `SpectatorService.getInstance().clearAll()` in `initialize()` is a red flag — this should not be called directly in the gamemode initialiser; the framework calls `clearAll()` in `setActiveMinigame()`. This could cause a double-clear. Player leave handling is missing (players who disconnect during a round are not removed from `activePlayers`).

---

### Death Shuffle

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Scoreboard, `PersistentMinigame` (partial), `DynamicParticipantMinigame`, `PauseAwareMinigame`, `PlayerLeaveAware`, `EntityDeathAware`

**Frameworks Missing:** Death Lifecycle (F05), Team Framework (F08), Kit (F14), Role (F15), Arena (F18), Protected Items (F13), Region Trigger (F10), Countdown Service (F19)

**Partial Integrations:**
- `PersistentMinigame` — partially implemented; `activePlayers`, `points`, and `assignedObjectives` may not be fully persisted
- The custom `DeathObjectiveRegistry` system is a parallel objective framework not connected to any shared objective abstraction

**Compliance Score:** 55/100

**Notes:** Death Shuffle is structurally very similar to Block Shuffle. Both share a round state machine, time warnings, active-player tracking, and point scoring. These are strong candidates for a shared **Objective Round Framework** (see Section 10).

---

### Duels

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, **Death Lifecycle (F05, fully wired)**, Global Match Rules, Map Protection, Arena (F18), Kit (F14), `SpawnPointAware`, Map Editor, `DuelsDeathCallbacks`, `DuelsDeathPolicy`, `DuelsSpectatorPolicy`, `DuelsRespawnStrategy`

**Frameworks Missing:** Team Framework (F08, partial — uses direct lists), Scoreboard (F12, no sidebar), Persistent Session (F06), Countdown Service (F19), Protected Items (F13), Role (F15)

**Partial Integrations:**
- Team Framework — uses raw `List<ServerPlayerEntity> team1, team2` rather than `TeamManager`. `TeamManagerProvider` not implemented. Arena-level team tracking exists in `DuelMatch`
- Death Lifecycle — **fully integrated** with proper `DeathAwareMinigame` implementation, custom policies (`DuelsDeathPolicy`, `DuelsSpectatorPolicy`, `DuelsRespawnStrategy`), and `DuelsDeathCallbacks`

**Compliance Score:** 71/100

**Notes:** Duels is the reference implementation for the Death Lifecycle Framework. The integration is well-structured with correct separation of policy/callback concerns. The absence of `TeamManager` in favour of raw lists is a gap to eventually address. No scoreboard is a minor gap.

---

### Murder Mystery

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, **Death Lifecycle (F05, fully wired)**, Global Match Rules, Map Protection, Scoreboard, Role (F15), Visibility (F16), Corpse (F17), `SpawnPointAware`, Map Editor, `PauseAwareMinigame`, `DynamicParticipantMinigame`, `MurderMysteryDeathCallbacks`, `MurderMysteryDeathPolicy`, `MurderMysterySpectatorPolicy`

**Frameworks Missing:** Team Framework (F08), Kit (F14), Arena (F18), Protected Items (F13), Countdown Service (F19), Persistent Session (full override), Region Trigger (F10)

**Partial Integrations:**
- `PersistentMinigame` — partially; roles, economy state, and coin spawner state are non-trivial to restore
- The virtual economy (`VirtualEconomyManager`, `ShopManager`, `CoinManager`) is a bespoke subsystem not connected to any shared framework

**Compliance Score:** 76/100

**Notes:** Murder Mystery is the **second reference implementation** for the Death Lifecycle Framework, alongside Duels. The role/visibility/corpse integration is well done. The virtual economy is the largest bespoke subsystem — a `VirtualEconomyFramework` could be valuable if other social/deduction gamemodes are added. `VisibilityManager` usage is correct.

---

### Bridge

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, **Region Trigger (F10)**, Team Framework (`TeamManager` + `TeamManagerProvider`), Scoreboard, Persistent Session (full), Player Snapshot, `SpawnPointAware`, `PauseAwareMinigame`, Map Editor, `InventoryLayoutAware`, `PlayerDamageAware`, `PlayerRegionAware`, VanillaTeamAdapter

**Frameworks Missing:** Death Lifecycle (F05), Kit (F14, gives items manually), Role (F15), Arena (F18, has its own field reset), Countdown Service (F19), Protected Items (F13)

**Partial Integrations:**
- `PersistentMinigame` — fully implemented with `saveRuntimeState()` including scores and state
- Death/respawn — handled inline (`onEntityDeath`, `onPlayerRespawn`) rather than through Death Lifecycle
- Region trigger usage is the only example of `PlayerRegionAware` in production

**Compliance Score:** 75/100

**Notes:** Bridge has the broadest framework adoption of any gamemode after Murder Mystery and Duels. The `PlayerRegionAware` usage for goal detection is exactly the correct pattern. The death/respawn handling (immediate respawn at team spawn, score update) would map cleanly to the Death Lifecycle Framework.

---

### Infection

**Frameworks Used:** Session, Match Lifecycle, Freeze, Spectator, Global Match Rules, Map Protection, Team Framework (`TeamManager` + `TeamManagerProvider`), Scoreboard, Persistent Session (full), Player Snapshot, `SpawnPointAware`, `PauseAwareMinigame`, `PlayerRespawnAware`, `PlayerDamageAware`, VanillaTeamAdapter

**Frameworks Missing:** Death Lifecycle (F05), Kit (F14), Role (F15), Visibility (F16), Arena (F18), Countdown Service (F19), Protected Items (F13), Region Trigger (F10)

**Partial Integrations:**
- `PersistentMinigame` — fully implemented with meaningful state (survivors, infected lists, ticks)
- Role Framework — Infection has survivor/infected roles handled directly via team membership rather than `RoleManager`
- Name-tag visibility — survivors and infected visibility rules applied via vanilla team colour/collision settings rather than `VisibilityManager`

**Compliance Score:** 66/100

**Notes:** Infection would significantly benefit from Role Framework adoption (to replace its `survivors`/`infected` set-based role tracking) and Visibility Framework adoption (name-tag rules for infected stealth). The death-conversion mechanic (death → join infected team) would be an interesting Death Lifecycle Framework use case.

---

## 5. Mandatory Framework Compliance

The following frameworks should be considered **mandatory** for all production competitive gamemodes.

---

### Session Framework (F01)
**Why Mandatory:** The session is how every gamemode is launched and how players are routed. Without it, there is no multiplayer deployment.  
**Current Adoption:** 11/11 (100%)  
**Missing Gamemodes:** None  
**Risks of Missing:** Game would never launch on backend; no player routing

---

### Match Lifecycle Framework (F02)
**Why Mandatory:** Provides the countdown, start signal, end signal, and return-to-lobby transfer. Without it, matches have no structured start/end and players cannot return to lobby.  
**Current Adoption:** 11/11 (100%) — via `AbstractMinigame`  
**Missing Gamemodes:** None  
**Risks of Missing:** No countdown, no lobby return, no admin end-game control

---

### Freeze Framework (F03)
**Why Mandatory:** Required for countdown freeze, admin pause, and consistent pre-game state. Without it, players can move and interact during the countdown phase.  
**Current Adoption:** 11/11 (100%) — via `AbstractMinigame`  
**Missing Gamemodes:** None  
**Risks of Missing:** Players exploit movement during countdown

---

### Spectator Framework (F04)
**Why Mandatory:** Every competitive gamemode has dead/eliminated players who need to spectate. Without a managed spectator service, those players get no camera control and their mode change is untracked (causing reconciliation bugs on respawn).  
**Current Adoption:** 11/11 (100%) — `SpectatorService` is available universally  
**Missing Gamemodes:** None (though gamemodes differ in whether they use it meaningfully)  
**Risks of Missing:** Eliminated players in undefined state; camera orphaning; no cleanup on disconnect

---

### Global Match Rules Framework (F07)
**Why Mandatory:** Ensures consistent gamerule state (keepInventory, pvp, weather, etc.) without leaking settings between sessions.  
**Current Adoption:** 11/11 (100%) — `configureGameRules()` is abstract in `AbstractMinigame`  
**Missing Gamemodes:** None  
**Risks of Missing:** Gamerule state leaks between sessions; unexpected player deaths from fall damage, etc.

---

### Map Protection Framework (F09)
**Why Mandatory:** Protects server maps from being destroyed by players. Without it, a single session can permanently damage the map for all future sessions.  
**Current Adoption:** 11/11 (100%) — via `MinigameEventRouter`  
**Missing Gamemodes:** None  
**Risks of Missing:** Permanent map damage; server restarts required between sessions

---

### Death Lifecycle Framework (F05) — *Should Be Mandatory*
**Why Mandatory:** Every competitive gamemode handles player death. Without centralisation, each gamemode reinvents spectator transitions, respawn timing, state machine management, and cleanup. The framework is already the canonical approach for Duels and Murder Mystery.  
**Current Adoption:** 2/11 (18%)  
**Missing Gamemodes:** Manhunt, Speedrun, Bounty Hunt, Death Swap, Resource Sprint, Block Shuffle, Death Shuffle, Bridge, Infection  
**Risks of Missing:** Death handling bugs isolated to individual gamemodes; no shared state machine; spectator cleanup inconsistencies; respawn location errors

---

## 6. Framework Adoption Ranking

| Rank | Framework | Adoption | Count |
|------|-----------|----------|-------|
| 1 | Session Framework (F01) | 100% | 11/11 |
| 1 | Match Lifecycle (F02) | 100% | 11/11 |
| 1 | Freeze Framework (F03) | 100% | 11/11 |
| 1 | Spectator Framework (F04) | 100% | 11/11 |
| 1 | Global Match Rules (F07) | 100% | 11/11 |
| 1 | Map Protection (F09) | 100% | 11/11 |
| 7 | Scoreboard (F12) | 91% | 10/11 |
| 8 | Persistent Session (F06) | 73% | 8/11 (partial) |
| 8 | Player Snapshot (F20) | 73% | 8/11 (partial) |
| 10 | Team Framework (F08) | 55% | 6/11 |
| 11 | Protected Items (F13) | 18% | 2/11 |
| 11 | Death Lifecycle (F05) | 18% | 2/11 |
| 13 | Map Editor (F11) | 36% | 4/11 confirmed + 7 partial |
| 14 | Arena Framework (F18) | 9% | 1/11 |
| 14 | Kit Framework (F14) | 9% | 1/11 |
| 14 | Role Framework (F15) | 9% | 1/11 |
| 14 | Visibility Framework (F16) | 9% | 1/11 |
| 14 | Corpse Framework (F17) | 9% | 1/11 |
| 14 | Countdown Service (F19) | 9% | 1/11 |
| 14 | Derangement/Swap (F21) | 9% | 1/11 |
| 14 | Respawn Policy (F22) | 9% | 1/11 |
| 14 | Region Trigger (F10) | 9% | 1/11 |
| 14 | Inventory Layout (F23) | 9% | 1/11 |

---

## 7. Framework Consistency Audit

---

### Issue 1 — GameState Enum Redundancy

**Location:** `minigame/core/GameState.java`

**Problem:** `GameState` has three separate values that all mean "actively running": `RUNNING`, `IN_PROGRESS`, and `PLAYING`. The `isActive()` method returns true for all three. Gamemodes set different ones (`BlockShuffleMinigame` and `DeathShuffleMinigame` use `IN_PROGRESS`; most others use `RUNNING`). This means code that checks `state == GameState.RUNNING` will incorrectly miss Block Shuffle and Death Shuffle.

Similarly, `WAITING` and `WAITING_FOR_PLAYERS` both appear; `FINISHED` and `ENDING`, `MATCH_OVER`, `RETURNING` all relate to post-game.

**Recommended Fix:** Consolidate to one canonical value per phase. Add a deprecation comment on all aliases. Short-term: ensure `isActive()` covers all aliases (already done). Long-term: normalise to `WAITING_FOR_PLAYERS`, `STARTING`, `RUNNING`, `PAUSED`, `ENDING`, `STOPPED` and update all gamemode state assignments.

---

### Issue 2 — SpectatorService.onPlayerLeave Does Not Clean Up Leaving Player

**Location:** `minigame/core/spectator/SpectatorService.java`

**Problem:** `onPlayerLeave(player)` removes the leaving player from the target lists of *other* spectator sessions but does not call `stopSpectating(player)` to remove the leaving player's *own* spectator session from `this.sessions`. This leaks the session entry and will cause incorrect behaviour if the player reconnects while a `SpectatorSession` for them still exists.

**Recommended Fix:** In `onPlayerLeave`, after updating other sessions, call `this.stopSpectating(player.getUuid(), SpectatorStopReason.PLAYER_LEFT)` unconditionally.

---

### Issue 3 — Countdown Deduplication Reimplemented in Every Gamemode

**Location:** `BountyHuntMinigame.java` (`announcedGraceThresholds`), `ResourceSprintMinigame.java` (`timeWarningsShown`), `BlockShuffleMinigame.java` (`timeWarningsShown`), `DeathShuffleMinigame.java` (`timeWarningsShown`), `BridgeMinigame.java` (timer logic)

**Problem:** `CountdownService` exists precisely to solve countdown deduplication, but five gamemodes implement the same `Set<Integer> timeWarningsShown` pattern manually. This creates six separate implementations of the same logic.

**Recommended Fix:** All gamemodes should use `CountdownService`. Remove per-gamemode `timeWarningsShown` sets and replace with `CountdownService.announceOnce()` and `CountdownService.announceVisibleCountdown()` calls.

---

### Issue 4 — MinigameManager Singleton (Flagged but Unresolved)

**Location:** `minigame/core/MinigameManager.java`

**Problem:** `MinigameManager.getInstance()` is a static singleton with a `// TODO: Migrate — remove MinigameManager singleton, convert to injected instance` comment. All gamemodes and services reference it statically. This makes testing difficult and creates implicit coupling.

**Recommended Fix:** This is a large refactor (Phase 7.5 as previously discussed). It should be isolated as its own PR and not mixed with Death Lifecycle migration.

---

### Issue 5 — BlockShuffleMinigame Calls SpectatorService.clearAll() in initialize()

**Location:** `minigame/impl/blockshuffle/BlockShuffleMinigame.java`

**Problem:** `initialize()` explicitly calls `SpectatorService.getInstance().clearAll()`. `MinigameManager.setActiveMinigame()` already calls `SpectatorService.getInstance().clearAll()` before calling `initialize()`. This double-clear is harmless but indicates the gamemode is incorrectly taking ownership of framework-level cleanup that the framework already handles.

**Recommended Fix:** Remove the `SpectatorService.clearAll()` call from `BlockShuffleMinigame.initialize()`.

---

### Issue 6 — Respawn Policy Controller (F22) Predates Death Lifecycle Framework (F05)

**Location:** `minigame/core/respawn/RespawnPolicyController.java`, used by `DeathSwapMinigame.java`

**Problem:** `RespawnPolicyController` is a simpler predecessor to `DeathLifecycleManager`. It covers `ELIMINATION` vs. `POINTS` respawn modes. Death Swap is the only user. As the Death Lifecycle Framework matures, this class becomes redundant.

**Recommended Fix:** Include DeathSwap in the Death Lifecycle Framework migration plan. Once migrated, deprecate and remove `RespawnPolicyController`.

---

### Issue 7 — Direct SpectatorService Usage in Gamemodes Not Yet Migrated

**Location:** `BlockShuffleMinigame.java`, `ManhuntSpeedrunnerRespawnSystem.java`, `DeathSwapMinigame.java`, `BountyHuntMinigame.java`, `SpeedrunMinigame.java`, `InfectionMinigame.java`

**Problem:** Multiple gamemodes call `SpectatorService.getInstance().startSpectating(...)` directly rather than through `DeathLifecycleManager`. This is the legacy pattern the framework is designed to replace. Post-migration, these should route through `DeathAwareMinigame.getDeathLifecycleManager()`.

**Recommended Fix:** These are migration candidates for the Death Lifecycle Framework. Mark with `// TODO: Migrate to DeathLifecycleManager` comments per the migration tracking standard.

---

### Issue 8 — PlayerTracker Duplicated in Manhunt and BountyHunt

**Location:** `ManhuntMinigame.java` (`private final PlayerTracker playerTracker`), `BountyHuntMinigame.java` (`private final PlayerTracker playerTracker`)

**Problem:** `PlayerTracker` is a shared utility class but it is instantiated independently in two gamemodes. This is correct (each game needs its own tracker instance), but the *usage pattern* (updatePositions in tick, tick-driven compass updates) is duplicated across both gamemodes.

**Recommended Fix:** This is acceptable as-is since the tracker is per-instance. The tick-update pattern could be extracted to a shared helper, but this is low priority.

---

## 8. Architecture Standardisation Opportunities

---

### Opportunity 1 — Objective Round Framework

**Current Implementations:**
- `BlockShuffleMinigame` — block assignment, round timer, point tracking, sudden death
- `DeathShuffleMinigame` — objective assignment, round timer, grace period, point tracking

**Duplication Level:** High — both gamemodes share: `RoundState` (INTERMISSION / ACTIVE), per-player assignment map, active player tracking, round timer ticks, time warnings, sudden death logic, point scoring, scoreboard line updates

**Proposed Framework:** `RoundObjectiveFramework` with `ObjectiveAssigner<T>`, `RoundPhaseController`, and a `RoundAwareMinigame` interface exposing `onRoundStart()`, `onObjectiveComplete(player)`, `onRoundEnd()`

**Expected Benefits:** Eliminates ~200 lines of duplicated round logic; enables Block Shuffle and Death Shuffle to share their round management with any future gamemode (e.g. a "Find the Biome" round game)

---

### Opportunity 2 — Shared Win Condition Evaluation

**Current Implementations:** Every gamemode implements its own win condition check inline in death/leave/tick handlers. Checks like "last team standing", "first to X points", "time ran out" appear in multiple places.

**Proposed Framework:** A `WinConditionEvaluator` interface with implementations (`LastTeamStanding`, `FirstToScore`, `TimedWin`, `ObjectiveComplete`) that are evaluated by the framework at key lifecycle points (player death, player leave, score change, tick).

**Expected Benefits:** Removes bespoke win-check code from all 11 gamemodes; consistent evaluation timing; unit-testable win conditions

---

### Opportunity 3 — Shared Elimination Announcement

**Current Implementations:** Duels (`DuelsDeathCallbacks`), Murder Mystery (`MurderMysteryDeathCallbacks`), Infection (inline), Bridge (inline) all build nearly identical kill announcement text: `"☠ [victim] was eliminated by [killer]"`.

**Proposed Framework:** A `DeathAnnouncementBuilder` utility or a default in `DeathLifecycleCallbacks` that generates a standard elimination message, with gamemodes overriding the format if needed.

**Expected Benefits:** Consistent death messaging across all gamemodes; easy to update formatting in one place

---

### Opportunity 4 — Shared Protection Overlay Lifecycle

**Current Implementations:** BountyHunt manually tracks invincibility ticks and calls `ProtectionOverlaySender.send()` on tick. Bridge applies invincibility status effect with its own timer. Infection applies effects directly.

**Proposed Framework:** A `RespawnProtectionController` that receives a player + duration and handles: freeze, invincibility, overlay send/update/clear, cleanup on disconnect.

**Expected Benefits:** Consistent respawn protection across all gamemodes; correct cleanup on disconnect

---

### Opportunity 5 — Infection/Manhunt Role Framework Migration

Both Infection (survivor/infected) and Manhunt (speedrunner/hunter) use team-membership-based role tracking that duplicates `RoleManager`'s purpose. Migrating them to the Role Framework would eliminate ad-hoc team-membership checks for role-based behaviour and enable the Visibility Framework to manage name-tag visibility.

---

## 9. Framework Readiness Assessment

| Framework | Completeness | Stability | Adoption | Production Ready |
|-----------|-------------|-----------|----------|-----------------|
| Session (F01) | Good | Good | Excellent | Yes (with known bugs) |
| Match Lifecycle (F02) | Excellent | Excellent | Excellent | Yes |
| Freeze (F03) | Excellent | Excellent | Excellent | Yes |
| Spectator (F04) | Good | Fair (bug: onPlayerLeave) | Excellent | Near (fix 1 bug) |
| Death Lifecycle (F05) | Good | Good | Poor | Partial (2 gamemodes) |
| Persistent Session (F06) | Good | Good | Fair | Partial |
| Global Match Rules (F07) | Excellent | Excellent | Excellent | Yes |
| Team Framework (F08) | Excellent | Excellent | Fair | Yes |
| Map Protection (F09) | Good | Excellent | Excellent | Yes |
| Region Trigger (F10) | Good | Good | Poor | Yes (limited users) |
| Map Editor (F11) | Good | Good | Fair | Yes |
| Scoreboard (F12) | Good | Good | Excellent | Yes |
| Protected Items (F13) | Good | Good | Poor | Yes (limited users) |
| Kit Framework (F14) | Good | Good | Poor | Yes (limited users) |
| Role Framework (F15) | Fair | Fair | Poor | Partial |
| Visibility (F16) | Fair | Fair | Poor | Partial |
| Corpse (F17) | Fair | Fair | Poor | Partial (basic impl) |
| Arena (F18) | Good | Good | Poor | Yes (Duels only) |
| Countdown Service (F19) | Good | Excellent | Poor | Yes (underused) |
| Player Snapshot (F20) | Good | Good | Fair | Yes |
| Derangement/Swap (F21) | Excellent | Excellent | Poor | Yes (limited users) |
| Respawn Policy (F22) | Fair | Fair | Poor | Deprecated by F05 |
| Inventory Layout (F23) | Fair | Fair | Poor | Partial |

---

## 10. Missing Framework Analysis

---

### Candidate A — Objective Round Framework

**Current Implementations:** Block Shuffle, Death Shuffle (described in Section 8 above)  
**Duplication Level:** High  
**Proposed Framework:** `RoundObjectiveFramework` (see Section 8)  
**Expected Benefits:** ~200 lines removed; consistent sudden-death and round-reset handling

---

### Candidate B — Virtual Economy Framework

**Current Implementations:** Murder Mystery (`VirtualEconomyManager`, `CoinManager`, `ShopManager`)  
**Duplication Level:** Low currently (only Murder Mystery)  
**Proposed Framework:** `VirtualEconomyFramework` with `EconomyManager`, `ShopItem`, `ShopProvider`, `CurrencySpawner`  
**Expected Benefits:** Reusable for any gamemode with in-game currency (e.g. a future Skyblock, SkyWars, or Bed Wars variant)

---

### Candidate C — Win Condition Framework

**Current Implementations:** All 11 gamemodes (see Section 8 above)  
**Duplication Level:** Very High  
**Proposed Framework:** `WinConditionEvaluator` with standard implementations  
**Expected Benefits:** Removes bespoke win-check code from all gamemodes; unit-testable

---

### Candidate D — Respawn Protection Framework

**Current Implementations:** BountyHunt, Bridge, Infection (all have separate respawn invincibility implementations)  
**Duplication Level:** Medium  
**Proposed Framework:** `RespawnProtectionController` (see Section 8 above)  
**Expected Benefits:** Consistent protection duration, overlay, and cleanup

---

### Candidate E — Late Join Framework Formalisation

**Current Implementation:** `LateJoinPolicy` interface exists on `MinigameDefinition`. `DefaultLateJoinPolicy` is the fallback. `DynamicParticipantMinigame` exists on `AbstractMinigame`. However, the actual late-join routing (who is eligible to join mid-game, how they are assigned a team/role) is still per-gamemode logic scattered across `SessionBootstrapper.Handler.onPlayerJoin()` overrides.  
**Duplication Level:** Medium  
**Proposed Framework:** Formalise `LateJoinPolicy.resolveTeam()` and `LateJoinPolicy.resolveRole()` implementations per gamemode; route all late-join teleports through `SpawnPointAware.teleportToSpawn()`  
**Expected Benefits:** Consistent late-join handling; no silent drop of players who join at wrong time

---

## 11. Technical Debt Analysis

| Item | Location | Type | Priority |
|------|----------|------|----------|
| `MinigameManager` singleton | `MinigameManager.java` | Architecture | High (Phase 7.5) |
| `SpectatorService.onPlayerLeave` bug | `SpectatorService.java` | Bug | Critical |
| `GameState` enum redundancy | `GameState.java` | Consistency | Medium |
| `RespawnPolicyController` vs Death Lifecycle | `RespawnPolicyController.java` | Duplication | Medium |
| `SpectatorService.getInstance()` in 6 gamemodes | Multiple gamemodes | Migration debt | Medium |
| `SpectatorService` singleton | `SpectatorService.java` | Architecture | Low (post-Phase 7.5) |
| `FreezeService` singleton | `FreezeService.java` | Architecture | Low (post-Phase 7.5) |
| `RegionTriggerService` singleton | `RegionTriggerService.java` | Architecture | Low (post-Phase 7.5) |
| `BlockShuffleMinigame.clearAll()` in initialize | `BlockShuffleMinigame.java` | Correctness | Low |
| `timeWarningsShown` deduplication in 5 gamemodes | Multiple gamemodes | Duplication | Low |
| `DeathObjectiveRegistry` is a bespoke parallel to a general Objective framework | `DeathShuffleMinigame` | Isolation | Low |
| Incomplete `PersistentMinigame` overrides in AbstractMinigame subclasses | Multiple | Silent gap | Low |

---

## 12. Recommended Standard Framework Stack

### Competitive PvP Gamemodes
*(Manhunt, Bounty Hunt, Duels, Bridge, Infection)*

**Required:**
- Session (F01), Match Lifecycle (F02), Freeze (F03), Spectator (F04), Death Lifecycle (F05), Global Match Rules (F07), Map Protection (F09)

**Recommended:**
- Team Framework (F08), Scoreboard (F12), Map Editor (F11), Player Snapshot (F20)

**Optional:**
- Protected Items (F13), Kit (F14), Arena (F18), Region Trigger (F10), Respawn Protection (Candidate D)

---

### Objective / Collection Gamemodes
*(Block Shuffle, Death Shuffle, Resource Sprint)*

**Required:**
- Session (F01), Match Lifecycle (F02), Freeze (F03), Spectator (F04), Global Match Rules (F07), Map Protection (F09)

**Recommended:**
- Scoreboard (F12), Countdown Service (F19), Persistent Session (F06), Objective Round Framework (Candidate A)

**Optional:**
- Team Framework (F08), Death Lifecycle (F05), Protected Items (F13)

---

### Team-Based Gamemodes
*(Manhunt, Resource Sprint, Bridge, Infection, Duels)*

**Required:**
- Session (F01), Match Lifecycle (F02), Freeze (F03), Spectator (F04), Global Match Rules (F07), Map Protection (F09), Team Framework (F08)

**Recommended:**
- VanillaTeamAdapter, Scoreboard (F12), Death Lifecycle (F05), Map Editor (F11)

**Optional:**
- Visibility (F16), Arena (F18), Win Condition Framework (Candidate C)

---

### Survival / Open World Gamemodes
*(Speedrun, Death Swap)*

**Required:**
- Session (F01), Match Lifecycle (F02), Freeze (F03), Spectator (F04), Global Match Rules (F07), Map Protection (F09)

**Recommended:**
- Persistent Session (F06), Scoreboard (F12), Death Lifecycle (F05)

**Optional:**
- Countdown Service (F19), Derangement/Swap (F21) [for Death Swap specifically]

---

### Social / Deduction Gamemodes
*(Murder Mystery)*

**Required:**
- Session (F01), Match Lifecycle (F02), Freeze (F03), Spectator (F04), Death Lifecycle (F05), Global Match Rules (F07), Map Protection (F09), Role Framework (F15), Visibility (F16)

**Recommended:**
- Scoreboard (F12), Corpse (F17), Map Editor (F11), Persistent Session (F06)

**Optional:**
- Virtual Economy Framework (Candidate B), Protected Items (F13)

---

## 13. Final Compliance Dashboard

| Gamemode | Compliance % | Missing Critical Frameworks | Production Ready |
|----------|-------------|----------------------------|-----------------|
| Murder Mystery | 76% | Death Lifecycle actively used ✅; missing: Kit, Role on full migration | ✅ Yes |
| Bridge | 75% | Death Lifecycle | ⚠️ Near |
| Manhunt | 72% | Death Lifecycle (bespoke system in place) | ⚠️ Near |
| Duels | 71% | Scoreboard, formal Team Framework | ✅ Yes |
| Bounty Hunt | 70% | Death Lifecycle | ⚠️ Near |
| Death Swap | 68% | Death Lifecycle (RespawnPolicy in place) | ⚠️ Near |
| Infection | 66% | Death Lifecycle, Role/Visibility | ⚠️ Near |
| Speedrun | 58% | Death Lifecycle, Persistent Session | ⚠️ Near |
| Resource Sprint | 62% | Death Lifecycle (no player death, low priority) | ✅ Yes |
| Block Shuffle | 54% | Death Lifecycle, PlayerLeave handling gap | ⚠️ Needs work |
| Death Shuffle | 55% | Death Lifecycle, persistent objectives | ⚠️ Needs work |

---

## 14. Final Assessment

### Current Architecture Maturity

The Miniverse codebase is in a **late-intermediate** stage of architectural maturity. The foundational layer (`AbstractMinigame`, `MatchLifecycleController`, `FreezeService`, `SpectatorService`, `GlobalMatchRules`, `MapProtectionManager`) is solid and universally adopted. The mid-tier frameworks (`DeathLifecycleManager`, `TeamManager`, `RoleManager`, `ArenaManager`) are well-designed but not universally adopted. The highest-tier frameworks (Virtual Economy, Objective Round, Win Condition) do not yet exist as shared abstractions.

### Scores

| Dimension | Score /10 | Justification |
|-----------|-----------|---------------|
| Framework Design | 8/10 | Excellent core design; opt-in interfaces are clean; policy/callback separation in Death Lifecycle is strong |
| Framework Adoption | 5/10 | Universal for the 6 base frameworks; very low for Death Lifecycle (18%) and other mid-tier frameworks |
| Framework Consistency | 6/10 | `GameState` redundancy and `CountdownService` underuse are the main gaps; `SpectatorService` has a confirmed bug |
| Gamemode Standardisation | 6/10 | All gamemodes use `AbstractMinigame`; but death handling, countdown, and role tracking are still per-gamemode |
| Production Readiness | 7/10 | Most gamemodes are deployable; 2 gamemodes (Block Shuffle, Death Shuffle) have notable gaps; 2 bugs confirmed |

---

### Top 10 Highest-Priority Framework Improvements

1. **Fix `SpectatorService.onPlayerLeave` leaving-player session leak** — confirmed bug; single-method fix; must precede any Death Lifecycle migration

2. **Migrate Murder Mystery to Death Lifecycle Framework** — already partially done; complete the migration per the corrected plan; use as proof-of-concept before migrating further gamemodes

3. **Consolidate `GameState` enum** — remove `IN_PROGRESS`, `PLAYING`, `WAITING`, `MATCH_OVER`, `ROUND_RESET`, `FINISHED` as redundant aliases; normalise all gamemodes to the canonical set; this is a cross-cutting change requiring compile-safe migration steps

4. **Migrate Death Swap to Death Lifecycle Framework and retire `RespawnPolicyController`** — Death Swap's elimination/points model maps directly to `PostDeathPolicy`; this removes a redundant framework

5. **Adopt `CountdownService` in all 5 gamemodes that reimplement it** — mechanical change; remove `timeWarningsShown` sets from Block Shuffle, Death Shuffle, Resource Sprint, Bounty Hunt, Bridge

6. **Fix `BlockShuffleMinigame.onPlayerLeave` gap** — currently no player leave handling; disconnected players remain in `activePlayers` indefinitely, blocking win-condition evaluation

7. **Migrate Block Shuffle and Death Shuffle to a shared Objective Round Framework** — these two gamemodes share >80% of round logic; extract into a reusable framework; remove `SpectatorService.clearAll()` from `BlockShuffleMinigame.initialize()`

8. **Complete Death Lifecycle migration sequence** — after Murder Mystery and Death Swap, proceed: Speedrun → BountyHunt → Infection → Bridge → Manhunt (last); add `// TODO: Migrate to DeathLifecycleManager` to all gamemodes not yet migrated

9. **Migrate `MinigameManager` singleton** (Phase 7.5) — convert to injected instance; this is the largest architectural change and must be isolated in its own PR; do not mix with Death Lifecycle work

10. **Introduce Win Condition Framework (Candidate C)** — as a new shared abstraction extracted from all 11 gamemodes; provides a unified, testable, and auditable win-condition evaluation mechanism; implement as part of a dedicated "Standardise Win Conditions" milestone

---

*End of Audit Report. Generated from live source code at `github.com/Fr0sT-x/miniverse` (branch: master).*
