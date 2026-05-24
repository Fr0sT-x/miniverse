# Miniverse Agent Guide

This project is a Fabric Minecraft minigame framework. It runs a main server with session management UI, then launches child backend servers for individual minigame sessions.

Minecraft version - 1.21.1

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
- Client transition overlays are coordinated through `network/TransitionTransferCoordinator` and `client/transition/TransitionOverlay` so server transfers are hidden behind a persistent global renderer instead of a normal Screen.

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
- `minigame/core/freeze`: reusable freeze service for movement blocking, freeze reasons, and lifecycle integration.
- `minigame/core/spectator`: reusable spectator framework (sessions, policies, camera control, target providers, restrictions, and lifecycle cleanup).
- `minigame/core/protection`: reusable protection overlay broadcaster utilities for invincibility/grace visuals.
- `minigame/core/vanilla`: adapter for mirroring custom teams into vanilla scoreboard teams.
- `network/SessionNetwork`: server-side GUI payload handlers and session snapshot creation.
- `network/TransitionTransferCoordinator`: wraps server transfer packets with a client overlay handshake. It sends `transition_start`, waits for `transition_ready`, then sends `ServerTransferS2CPacket`, with a short timeout fallback.
- Velocity production mode is enabled by `config/miniverse/proxy.json` with `velocityEnabled=true`. In that mode `TransitionTransferCoordinator` keeps the overlay handshake but sends `miniverse:velocity` proxy plugin messages instead of `ServerTransferS2CPacket`; the companion Velocity plugin dynamically registers child backends and moves players through the proxy.
- Backend match startup has a separate readiness handshake from transfer covering. `SessionBootstrapper` marks expected players as `LOADING` on backend join, freezes them with `MATCH_LOADING`, sends match intro data and team/member readiness data to the global transition overlay, and waits for `client_match_ready` after the client reports world/chunk/render readiness. `MatchLifecycleController.beginMatch(...)` must only run after all expected clients are ready. If any expected client times out, startup is aborted, remaining players are unfrozen and returned to the lobby instead of being left behind the overlay.
- `session/SessionManager`: in-memory session registry, player assignment, backend launching, transfer packets, and stop/remove lifecycle.
- `session/ServerLauncher`: prepares backend working dirs, links them to a standalone Fabric server runtime, writes `miniverse-session.json`, `ops.json`, and `server.properties`, builds the child JVM command, and waits for ports.
- Session backend ports are configured in `config/miniverse/session-launcher.json`. `sessionPortStart`/`sessionPortEnd` are the local child server bind ports; optional `publicSessionPortStart`/`publicSessionPortEnd` map those local ports to the externally forwarded ports sent in transfer packets. If the public range is omitted, transfers use the local port numbers directly.
- `session/SessionOperatorSnapshot`: captures assigned players who are vanilla operators on the main server before backend launch and writes equivalent backend `ops.json` entries.
- `session/SessionRegistry`: persists session snapshots under `run/sessions/<sessionId>/session.json`; still reads legacy `session.properties`. Seed-change restarts are coordinated through registry lifecycle flags and per-group replacement targets so the main server can stage a new backend while players remain in the old backend, then the old backend transfers them directly to the replacement.
- Session startup cleanup is retention-based, not destructive. Keep the newest retained session folders and prune only older stale folders according to `config/miniverse/session-retention.json`; do not delete every previous session on startup.
- `common/MiniverseFileUtils`: shared filesystem helpers for link-aware session cleanup. Use this for deleting session folders so Windows junctions are removed as links instead of traversed into the shared runtime.
- `client/gui`: session selector, setup screens, session snapshot cache, and NBT plan builders.

## Runtime Architecture

Main server flow:

1. `Miniverse.onInitialize()` calls `MiniverseGames.registerAll()`.
2. Commands and `SessionNetwork` create a `GameSession` through `SessionCreationService`.
3. `SessionPlan` validates settings, seed plan, teams, roles, and launch intent.
4. `SessionManager.launchSession()` launches backend servers according to the session topology.
5. `SessionManager.launchSession()` captures assigned main-server operators before players transfer; `ServerLauncher` writes those entries to each child server's `ops.json`.
6. `ServerLauncher` writes runtime config and starts child Fabric dedicated servers through `java -jar fabric-server-launch.jar nogui`.
7. Main server transfers players using `ServerTransferS2CPacket`.

Backend server flow:

1. Child server starts with `-Dminiverse.session.config=<path>`.
2. Each gamemode bootstrapper uses `SessionBootstrapper.register(...)`.
3. On player join, `SessionBootstrapper` reads `miniverse-session.json`, creates the runtime if needed, applies settings once, adds expected participants, assigns team/role data, marks them `LOADING`, and sends reusable match-introduction data to the transition overlay.
4. Clients keep the global transition overlay visible until their world, player, local chunk, and renderer are stable for the configured grace ticks, then send `client_match_ready` to the backend. Players who never become ready within 60 seconds are disconnected from the backend.
5. Once all expected online players are client-ready and the gamemode reports `canStart()`, `MatchLifecycleController` starts the frozen countdown. The overlay remains visible through the countdown and is released by the controller exactly when the match enters `RUNNING`.
6. `MatchLifecycleController` freezes participants, clears inventories, fills food/saturation, shows the countdown actionbar, then invokes the gamemode `startGame()` callback. The gamemode title/objective remains on the transition overlay instead of being sent as a vanilla title at match start.
7. `MinigameEventRouter` first lets `MatchLifecycleController` suppress frozen interactions, then forwards Fabric events only to the active runtime and only through opt-in interfaces.
8. Backend session GUI requests are answered from the local `miniverse-session.json`; child servers should not rely on the main server's session registry being present in their working directory.
9. Backend lifecycle stop/return flags must be written to the main server's session registry root recorded in `miniverse-session.json`. This lets the main server's join routing see `stopRequested`/`returnComplete` and prevents returned players from being bounced back into the finished session.
| 10. Retained session worlds may be inspected from the settings history tab. Inspection launches must copy the retained `world/` into `run/session-inspections/` and launch that copy in spectator-safe mode; never boot or mutate the retained session folder directly. The inspection copy receives a minimal `miniverse-session.json` with only `return.host`, `return.port`, and `registry.sessionsRoot` so players can return to the main server. Inspection backends are launched with `-Dminiverse.inspection=true` and run in spectator gamemode, preventing mutations to the world.

## Match Lifecycle Framework

`MatchLifecycleController` owns common lifecycle transitions across backend minigame sessions. It is generic and must not contain gamemode-specific branches.

Start flow:

1. `SessionBootstrapper` calls `MatchLifecycleController.beginMatch(runtime, options, minigame::startGame)` when all expected players are online, all expected clients have sent `client_match_ready`, and the gamemode reports it can start.
2. The controller transitions through `STARTING`/`FROZEN`, freezes participants for the configured duration, clears inventories, fills food and saturation, and displays countdown actionbar text. The default start freeze is 15 seconds; the transition overlay reveals at 5 seconds remaining so players see the loaded world for the last 10 seconds while still frozen.
3. Late-joining participants during `START_FREEZE` are added and frozen through `MatchLifecycleController.onParticipantJoin(...)` (invoked from `MinigameEventRouter`).
4. During freeze, global routing blocks movement input via the freeze service, prevents frozen interactions, and keeps participants immobile without per-tick teleport spam. Gamemode-specific post-start freezes, such as Manhunt's hunter release delay, should begin from the gamemode `startGame()` callback after the shared match freeze ends.
5. When the countdown ends, the controller unfreezes participants, moves the runtime into `RUNNING`, and invokes the gamemode start callback.

End flow:

1. When a gamemode has winner data, call `MatchLifecycleController.getInstance().endMatch(runtime, MatchEndResult, MatchLifecycleOptions)`.
2. The controller transitions to `ENDING`/`RETURNING`, freezes gameplay interactions, shows `YOU WIN` or `YOU LOST` titles, shows the winner label as visible countdown/subtitle context, and starts the return countdown.
3. During return countdown, only admins/operators/session managers receive a bold clickable cancel message with hover text. The click runs `/miniverse_cancel_return`.
4. If cancelled, return transfer is aborted, admins are notified, and the session remains alive in `ENDING`.
5. If not cancelled, participants receive `ServerTransferS2CPacket` back to `SessionRuntimeConfig`'s return host/port and the runtime moves to `FINISHED`.

Default phases are `WAITING`/`WAITING_FOR_PLAYERS`, `STARTING`, `FROZEN`, `RUNNING`/`IN_PROGRESS`, `ENDING`, `RETURNING`, and `FINISHED`. Existing `WAITING_FOR_PLAYERS` and `IN_PROGRESS` names remain for compatibility, but new lifecycle code should prefer the generic phases where possible.

Gamemodes customize lifecycle behavior by passing `MatchLifecycleOptions`; do not hardcode game ids in lifecycle code. Backend bootstrappers can override `SessionBootstrapper.Handler.lifecycleOptions(...)` to provide transition-overlay title/subtitle text, freeze duration, return duration, sounds, and teleport behavior. Use the subtitle for one concise sentence explaining the mode objective or win condition.
Disconnect grace: use `MatchLifecycleOptions.disconnectGraceSeconds` with a `DisconnectGraceHandler` to delay critical win conditions when key players disconnect, and restore the match if they reconnect in time.

## Protection Overlay Framework

`minigame/core/protection` provides translucent client-side rendering for temporary player protection effects (respawn invincibility, grace periods, etc). The system uses Fabric 1.21.1 entity feature renderers with proper depth testing, so overlays automatically hide behind walls without manual line-of-sight checks. The client tracks overlays by player UUID and overlay id, then renders the highest-priority active effect as a low-alpha second pass over the player model. Key components:

- `ProtectionOverlayClient`: Client-side overlay state manager with automatic expiration tracking
- `ProtectionOverlayFeatureRenderer`: Fabric feature renderer for translucent aura rendering over player models
- `ProtectionOverlaySender`: Server-side broadcaster with convenience methods for standard overlay types
- `ProtectionOverlayPresets`: Pre-configured overlay styles (respawn protection, grace period, etc.)

Gamemodes send overlays via `ProtectionOverlaySender.broadcast(...)` or `send(...)` with custom ARGB colors and durations. The overlay framework automatically handles network syncing, client-side expiration, and proper translucent rendering with GPU depth testing (no flickering, no wallhacks). See `markdown/PROTECTION_OVERLAY_ARCHITECTURE.md` and `markdown/PROTECTION_OVERLAY_QUICK_START.md` for detailed documentation.

## Spectator Framework

`minigame/core/spectator` provides a reusable spectator system with per-player `SpectatorSession` state, policy-driven restrictions, and a camera controller that locks/validates targets to prevent freecam exploits. Gamemodes should request spectator behavior through `SpectatorService.startSpectating(...)` and supply a `SpectatorPolicy` plus `SpectatorTargetProvider` instead of embedding custom spectator logic. The service integrates with `MinigameEventRouter` for join/leave/respawn/death events and validates active sessions on server tick to reattach cameras and enforce restrictions.

## Protected Item Framework

`minigame/core/item` defines server-authoritative protected item rules (drop/transfer/deletion/duplication controls, optional slot locking, optional auto-restore) and centralized validation for inventory interactions. Gamemodes register protected item types and rules (identified by data components/NBT) while the framework enforces all handling in one place; gamemodes only provide item tagging plus restoration behavior when enabled. The enforcement pass treats the active screen-handler cursor stack as a temporary holder, so auto-restore must not duplicate items that are being moved inside the player inventory. Use `TrackingItemNameFormatter` to standardize tracking item display names across gamemodes.

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

Operator sync follows the same topology boundary. Shared-world backends receive OP entries for all assigned groups hosted by that backend. Isolated-world backends receive OP entries only for the assigned players in their own group.

IDE-launched backend sessions also receive `-Dminiverse.devSession=true` when the main server dev bypass is enabled. A standalone backend is not a Fabric/Loom development environment, so this explicit flag is the only dev-only signal it should use. In that mode, joining players are granted temporary backend operator access dynamically; production continues to rely on `ops.json` snapshots.

Examples:

- Manhunt, Bounty Hunt, Resource Sprint, Death Swap: shared-world.
- Speedrun: isolated-world.

Do not hardcode topology rules in generic framework systems. Add topology-specific behavior behind gamemode definitions or narrow session abstractions.

## Backend Server Runtime

Backend sessions always launch real standalone Fabric dedicated servers. Do not reintroduce Loom launch internals, `KnotServer`, `devlaunchinjector`, `launch.cfg`, or IDE classpath launching for sessions.

Runtime layout:

- Shared runtime: `fabric-server-launch.jar`, `server.jar`, `libraries/`, `mods/`, `config/`, and optionally `versions/`.
- Session folder: `world/`, `logs/`, `server.properties`, `eula.txt`, `ops.json`, and `miniverse-session.json`.

Development runtime discovery:

- `-Dminiverse.serverRuntime=<path>` or `MINIVERSE_SERVER_RUNTIME` wins first.
- In dev, the preferred default is `<project>/server-runtime`.
- `<project>/run/server-runtime` remains supported as a legacy fallback.
- In production, the server root is the working directory tree containing `fabric-server-launch.jar` and `mods/`.

Windows backend sessions use directory junctions for runtime directories and hard links for runtime jars when possible. Cleanup must delete junctions as links and must not recurse through them into the shared runtime.

In development, `ServerLauncher` runs `gradlew remapJar --no-daemon` before each backend launch, removes the previous Miniverse jar from the runtime `mods/` folder, and copies the freshly remapped jar from `build/libs`. Other runtime mods, such as Fabric API, must be left intact.

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
- Transfer and match startup hiding is client-side and global: `client/transition/TransitionOverlay` renders through HUD and screen hooks, persists across server/world changes, waits for player/world/chunk render readiness, sends backend client-ready once stable, presents game/team/map context with all teams, members, and per-member loading indicators, and only slides away when the backend releases it at match start. The overlay includes a `Disconnect` button so players can leave safely if a transfer or backend readiness flow stalls.

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
- Preserve main-server operator status at backend launch time through `SessionOperatorSnapshot`; session servers are isolated processes and must receive their own `ops.json`.
- Framework systems should depend on interfaces/definitions, not concrete gamemode classes.
- Cleanup matters: participants, scoreboards, vanilla teams, pending respawn maps, cooldown maps, and launched processes all need explicit lifecycle handling.
- Startup cleanup must preserve recovery/debug data. Do not restore "delete all sessions on startup"; use `SessionRetentionConfig` and link-aware deletion through `MiniverseFileUtils`.

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
