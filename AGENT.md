# Miniverse Agent Guide

This project is a Fabric Minecraft minigame framework. It runs a main server with session management UI, then launches child backend servers for individual minigame sessions.

Use this file as the first stop before changing framework code.

After changing any framework / implementing new frameworks, make sure to update this AGENT.md file. (if any)

## Architecture Overview

- `dev.frost.miniverse.Miniverse` is the server entrypoint. It registers gamemodes, commands, event routing, session routing, and shared networking payloads.
- `src/client/java/dev/frost/miniverse/client` contains the client GUI and client-side network receiver for session metadata.
- `minigame/core` is the framework layer: minigame contracts, registry, runtime context, event routing, session bootstrapping, lifecycle sequencing, scoreboard helpers, and vanilla team mirroring.
- `minigame/core/lifecycle` is the reusable match lifecycle layer for pre-start freeze, countdowns, end titles, return countdowns, admin return cancellation, and return transfer.
- `minigame/impl/<game>` contains each gamemode's definition, runtime, settings, bootstrapper, and event registration.
- `session` manages main-server sessions, backend process launch, transfer routing, persistent session JSON, seed plans, and launch topology.
- `session/plan` parses GUI-created NBT plans into validated session/team/role plans.
- `team` is the gameplay team authority used by minigame runtimes.
- `network` and `common/NetworkConstants` define Fabric custom payloads for the session GUI.

## Core Concepts

- `MinigameDefinition`: static gamemode registration contract. This is becoming the source of truth for id, display name, topology, metadata, commands, events, launch/session properties, and setup UI metadata.
- `Minigame`: active gameplay runtime contract (`initialize`, `startGame`, `stopGame`, death/state hooks).
- `MinigameRuntime`: wraps one active `Minigame` with `MinigameContext` and framework state.
- `MinigameContext`: runtime services: UUID-based participants, server binding, game clock, and state updater.
- `MatchLifecycleController`: global backend lifecycle coordinator for validated start/end sequences, frozen interaction suppression, countdowns, end titles, return teleport scheduling, admin cancellation, and transfer back to the configured lobby server.
- `MatchLifecycleOptions`: per-minigame customization for freeze duration, return duration, title text, sounds, countdown behavior, freezing, and return teleport enablement.
- `MatchEndResult`: generic winner payload used by gamemodes when invoking the standard end sequence.
- `ParticipantSet`: stores player identity by UUID and resolves `ServerPlayerEntity` only when needed.
- `SessionPlan`: client/server creation plan containing game id, session name, settings, teams, roles, seed mode, and auto-launch flag.
- `GameSession`: main-server session record. Owns settings, seed plan, launch groups, lifecycle state, and session id.
- `SessionGroup`: one launch group inside a session. It has a gameplay team plus a `BackendInstance`.
- `BackendInstance`: actual child Minecraft server process state. Do not confuse this with gameplay teams.
- `SessionGameDescriptor`: session-layer view of a registered `MinigameDefinition`; it carries id, display name, topology, and the definition reference.
- `SessionTopology`: `SHARED_WORLD` means all groups share one backend/world; `ISOLATED_WORLD` means each group gets its own backend/world.

## Package Responsibilities

- `command`: legacy/manual gamemode commands and pending command integration.
- `common`: shared constants and payload codecs used by both client and server.
- `chat`: server-side chat routing and channel policies for matches.
- `minigame/MiniverseGames`: central list of registered definitions.
- `minigame/core/event`: Fabric event router plus small opt-in interfaces (`ServerTickAware`, `PlayerRespawnAware`, etc.).
- `minigame/core/lifecycle`: generic match start/end sequencing, countdowns, player freeze enforcement, admin return cancellation, and backend return transfer.
- `minigame/core/vanilla`: adapter for mirroring custom teams into vanilla scoreboard teams.
- `network/SessionNetwork`: server-side GUI payload handlers and session snapshot creation.
- `session/SessionManager`: in-memory session registry, player assignment, backend launching, transfer packets, and stop/remove lifecycle.
- `session/ServerLauncher`: prepares backend working dirs, writes `miniverse-session.json` and `server.properties`, builds the child JVM command, and waits for ports.
- `session/SessionRegistry`: persists session snapshots under `run/sessions/<sessionId>/session.json`; still reads legacy `session.properties`.
- `client/gui`: session selector, setup screens, session snapshot cache, and NBT plan builders.

## Runtime Architecture

Main server flow:

1. `Miniverse.onInitialize()` calls `MiniverseGames.registerAll()`.
2. Commands and `SessionNetwork` create a `GameSession` through `SessionCreationService`.
3. `SessionPlan` validates settings, seed plan, teams, roles, and launch intent.
4. `SessionManager.launchSession()` launches backend servers according to the session topology.
5. `ServerLauncher` writes runtime config and starts child Fabric servers.
6. Main server transfers players using `ServerTransferS2CPacket`.

Backend server flow:

1. Child server starts with `-Dminiverse.session.config=<path>`.
2. Each gamemode bootstrapper uses `SessionBootstrapper.register(...)`.
3. On player join, `SessionBootstrapper` reads `miniverse-session.json`, creates the runtime if needed, applies settings once, adds expected participants, assigns team/role data, and enters the generic lifecycle start sequence when `canStart()` passes.
4. `MatchLifecycleController` freezes participants, clears inventories, fills food/saturation, shows the minigame title and countdown, then invokes the gamemode `startGame()` callback.
5. `MinigameEventRouter` first lets `MatchLifecycleController` suppress frozen interactions, then forwards Fabric events only to the active runtime and only through opt-in interfaces.

## Match Lifecycle Framework

`MatchLifecycleController` owns common lifecycle transitions across backend minigame sessions. It is generic and must not contain gamemode-specific branches.

Start flow:

1. `SessionBootstrapper` calls `MatchLifecycleController.beginMatch(runtime, options, minigame::startGame)` when all expected players are online and the gamemode reports it can start.
2. The controller transitions through `STARTING`/`FROZEN`, freezes participants for the configured duration, clears inventories, fills food and saturation, shows the minigame title plus gamemode-specific subtitle, and displays countdown actionbar text.
3. Late-joining participants during `START_FREEZE` are added and frozen through `MatchLifecycleController.onParticipantJoin(...)` (invoked from `MinigameEventRouter`).
4. During freeze, global event routing blocks movement escape, jumping by teleporting to the frozen position, interaction, entity/block attacks, block breaking/placing, and dropped frozen-player items.
5. When the countdown ends, the controller unfreezes participants, moves the runtime into `RUNNING`, and invokes the gamemode start callback.

End flow:

1. When a gamemode has winner data, call `MatchLifecycleController.getInstance().endMatch(runtime, MatchEndResult, MatchLifecycleOptions)`.
2. The controller transitions to `ENDING`/`RETURNING`, freezes gameplay interactions, shows `YOU WIN` or `YOU LOST` titles, shows the winner label as visible countdown/subtitle context, and starts the return countdown.
3. During return countdown, only admins/operators/session managers receive a bold clickable cancel message with hover text. The click runs `/miniverse_cancel_return`.
4. If cancelled, return transfer is aborted, admins are notified, and the session remains alive in `ENDING`.
5. If not cancelled, participants receive `ServerTransferS2CPacket` back to `SessionRuntimeConfig`'s return host/port and the runtime moves to `FINISHED`.

Default phases are `WAITING`/`WAITING_FOR_PLAYERS`, `STARTING`, `FROZEN`, `RUNNING`/`IN_PROGRESS`, `ENDING`, `RETURNING`, and `FINISHED`. Existing `WAITING_FOR_PLAYERS` and `IN_PROGRESS` names remain for compatibility, but new lifecycle code should prefer the generic phases where possible.

Gamemodes customize lifecycle behavior by passing `MatchLifecycleOptions`; do not hardcode game ids in lifecycle code. Backend bootstrappers can override `SessionBootstrapper.Handler.lifecycleOptions(...)` to provide the start title/subtitle, freeze duration, return duration, sounds, and teleport behavior. Use the subtitle for one concise sentence explaining the mode objective or win condition.

## Team System

There are two team layers:

- `dev.frost.miniverse.team.*`: gameplay authority for active minigames. Use this for roles, win logic, targeting, friendly-fire decisions, progress ownership, and team membership.
- `dev.frost.miniverse.session.PlannedTeam`: launch/session planning team. Use it to decide who is assigned to which backend group.

Gameplay teams have two common shapes:

- One-player teams: a player is treated as their own team for solo/FFA scoring, isolated progress, or individual win logic.
- Multi-player teams: multiple players share one team id/label, progress, and win/loss outcome.

Vanilla scoreboard teams are not gameplay authority. They are a visual/helper layer for nametags, prefixes, colors, collision, invisibility, and friendly-fire display behavior. Use `VanillaTeamAdapter` to mirror custom team snapshots into vanilla teams, and clear it on stop.

Prefer UUIDs for identity. Resolve `ServerPlayerEntity` only at the edge: messaging, inventory, effects, teleporting, scoreboard sync, and other live server operations.

## Backend Topology

Topology belongs to the gamemode. Session code reads it through `SessionGameDescriptor`, which is built from `MinigameDefinition`.

- `SHARED_WORLD`: `SessionManager` launches one primary backend and attaches other groups to that backend. Runtime config includes all groups.
- `ISOLATED_WORLD`: each group launches its own backend with the same seed plan.

Examples:

- Manhunt, Bounty Hunt, Resource Sprint, Death Swap: shared-world.
- Speedrun: isolated-world.

Do not hardcode topology rules in generic framework systems. Add topology-specific behavior behind gamemode definitions or narrow session abstractions.

## Registration

To register a gamemode:

1. Implement `MinigameDefinition`.
2. Add it in `MiniverseGames.registerAll()`.
3. Register commands/events through the definition.
4. Register backend startup through the gamemode's `*GameEvents.register()` -> `*SessionBootstrap.register()`.

`MinigameRegistry` normalizes ids, rejects duplicates, exposes metadata to the client, and calls each definition for commands/events.

## Session Creation

Client setup screens send `NetworkConstants.CreateSessionPayload(game, name, plan)`.

The plan should contain:

- `game`: definition id.
- `name`: display/session name.
- `launch`: whether to auto-launch after creation.
- `settings`: gamemode settings NBT, including `seedMode` and optionally `seed`.
- `groups`: optional team/group list. Empty groups means "assign all online players" for explicit plans.

`SessionCreationService` turns the plan into a `GameSession`, applies groups, stores settings, and returns whether to launch. `ServerLauncher` then calls `MinigameDefinition.writeSessionProperties()` and `writeLaunchProperties()` to serialize settings for child servers.

## UI and HUD

- Client opens with Right Shift or `/mg`.
- `MiniverseClient` requests a server snapshot and opens `SessionScreen` when `SESSION_LIST_ID` arrives.
- `SessionNetwork.sendSessionList()` sends sessions, online roster, launcher settings, and `MinigameDefinition.metadata()`.
- `SessionScreen` builds gamemode entries from metadata.
- `setupKind=CUSTOM` requires a custom setup screen mapped in `SessionScreen.CUSTOM_SETUP_SCREENS`.
- `setupKind=GENERIC` uses `GenericSetupScreen` and `MinigameMetadata.SetupField`.
- In-game HUD/scoreboard is currently server-side scoreboard based. Use `ScoreboardController` for sidebar objectives and `GameMessenger` for chat/title events.

## Networking and Events

Networking rules:

- Define payload ids and codecs in `NetworkConstants`.
- Register payload types once through `NetworkConstants.registerPayloadTypes()` on both client and server.
- Register server receivers in `SessionNetwork`.
- Register client receivers in `MiniverseClient`.
- Validate permissions in server handlers with `SessionPermissions` before mutating session state.
- Keep payloads small and structured. Prefer NBT for dynamic GUI plans/snapshots; prefer typed records for fixed actions.

Event rules:

- Generic Fabric events belong in `MinigameEventRouter`.
- Gamemode runtimes opt in by implementing small interfaces from `minigame/core/event`.
- Gamemode-specific bootstrap registration belongs in `*GameEvents.register()`, not in `Miniverse`.
- Avoid adding `if game == X` branches to framework routers.

## Extension Guides

### Add a New Gamemode

1. Create `minigame/impl/<id>/<Name>Definition.java`.
2. Create `<Name>Minigame.java` implementing `Minigame`; implement event-aware interfaces only as needed.
3. Create `<Name>Settings.java` with `defaults()`, `fromNbt(...)`, `fromProperties(...)`, and `writeTo(...)` if configurable.
4. Create `<Name>SessionBootstrap.java` using `SessionBootstrapper.Handler`.
5. Create `<Name>GameEvents.java` and call the bootstrapper there.
6. Add the definition to `MiniverseGames.registerAll()`.
7. Add a custom setup screen or expose generic metadata fields.

### Add a Setting or Config

1. Add the field to the gamemode settings record/class.
2. Parse it from setup NBT.
3. Write it from `MinigameDefinition.writeSessionProperties()`.
4. Parse it from runtime `Properties` in the backend bootstrapper.
5. If needed by JVM/system properties, also write it from `writeLaunchProperties()`.
6. Add UI controls in the custom setup screen or add a `MinigameMetadata.SetupField` for generic setup.
7. Keep defaults stable so old session JSON still loads.

### Add HUD or Scoreboard Elements

1. Keep state in the runtime, not in scoreboard entries.
2. Use `ScoreboardController` for sidebar display.
3. Update at predictable intervals, usually from `ServerTickAware`.
4. Clear objectives in `stopGame()` and when ending.
5. Use `GameMessenger` for titles/chat announcements.
6. If adding client HUD later, add a typed payload/snapshot and keep rendering client-only.

### Add Networking Payloads Safely

1. Add a `CustomPayload.Id` and payload record in `NetworkConstants`.
2. Add a `PacketCodec`.
3. Register C2S/S2C in `registerPayloadTypes()`.
4. Add server handling in `SessionNetwork` or a focused network class.
5. Add client handling in `MiniverseClient` or the relevant client module.
6. Server handlers must validate permissions, session existence, ids, and ranges.
7. Do not trust client NBT; re-validate through `SessionPlan` or equivalent server-side parsing.

### Add Team-Based Mechanics

1. Use `team.TeamManager` inside the runtime for gameplay teams and roles.
2. Decide whether the mode uses one-player teams, multi-player teams, or both. FFA/solo scoring should still model each player as a one-player gameplay team when shared team APIs simplify win logic.
3. Store UUIDs in maps/sets; resolve live players only when applying effects or messages.
4. Use `TeamRole` or add a role there only if it is framework-level.
5. For gamemode-specific roles, map them to `TeamRole` only where vanilla/team display needs it.
6. Sync visuals with `VanillaTeamAdapter.syncSnapshots(...)`.
7. Never read vanilla scoreboard teams to decide gameplay outcomes.

### Add Session Topology Behavior

1. Start from `MinigameDefinition.topology()`.
2. Keep generic launch differences in `SessionManager`/`ServerLauncher`.
3. Keep gamemode-specific behavior in the gamemode definition, settings serialization, or bootstrapper.
4. For shared-world sessions, remember runtime config contains all groups.
5. For isolated-world sessions, each backend receives only its assigned group.
6. Do not treat backend instances as gameplay teams; a backend can host multiple gameplay teams in shared-world mode.

### Integrate Vanilla Scoreboard Teams

1. Keep a `VanillaTeamAdapter` field in the runtime with a namespace.
2. Build `TeamSnapshot` values from custom team state.
3. Provide `VanillaTeamOptions` for color, prefix, collision, friendly fire, visibility, etc.
4. Call sync when membership changes and periodically if needed.
5. Call `clear(server)` on stop/end.
6. Keep scoreboard team names internal; Minecraft limits names to 16 chars and the adapter handles this.

## Compatibility and Deprecated Paths

- `GameSession` still exposes `assignment` aliases for older code; prefer `groups`.
- `SessionGroup` still has `assignmentLabel` compatibility in runtime JSON.
- `SessionRegistry` still reads legacy `session.properties`; new code writes `session.json`.
- `dev.frost.miniverse.session.PlannedTeam` is for session launch planning; `dev.frost.miniverse.team.GameTeam` is for active gameplay. Do not mix them.
- Deleted/old `PlayerAssignment` terminology may still appear in docs or compatibility aliases. Prefer group/team terminology.

## Conventions

- Gamemode ids are lowercase registry ids (`manhunt`, `resource_sprint`, etc.).
- Settings property keys should be namespaced by gamemode id: `manhunt.runnerLives`, `bountyhunt.scoreToWin`.
- JVM properties should use `miniverse.<game>.<setting>`.
- Keep `register()` methods idempotent.
- Use `Properties` for backend bootstrap compatibility and JSON/NBT for structured session data.
- Use UUIDs for long-lived identity, not `ServerPlayerEntity`.
- Framework systems should depend on interfaces/definitions, not concrete gamemode classes.
- Cleanup matters: participants, scoreboards, vanilla teams, pending respawn maps, cooldown maps, and launched processes all need explicit lifecycle handling.

## Common Mistakes

- Adding gamemode-specific branches to `SessionManager`, `MinigameEventRouter`, `SessionNetwork`, or `ServerLauncher`.
- Using vanilla scoreboard teams as the source of gameplay truth.
- Holding `ServerPlayerEntity` in long-lived maps across respawns or transfers.
- Forgetting to update both setup NBT parsing and backend `Properties` parsing for a new setting.
- Adding a custom setup screen but not adding it to `SessionScreen.CUSTOM_SETUP_SCREENS`.
- Registering payload types on only one side.
- Launching shared-world logic as if every team has its own process.
- Hardcoding registered gamemode ids in session systems instead of resolving `MinigameDefinition`.

## Architectural Direction

- Move session creation and topology decisions toward `MinigameDefinition` as the single source of truth.
- Keep custom teams authoritative and vanilla teams purely visual.
- Keep topology controlled by gamemodes, not framework hardcoding.
- Preserve shared-world and isolated-world sessions as first-class modes.
- Keep backend instances separate from gameplay teams.
- Keep runtime identity UUID-first.
- Prefer small framework extension points over broad global hooks.
- Add generic metadata-driven UI where possible; use custom setup screens only when the workflow needs richer drafting or validation.
