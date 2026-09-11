# Miniverse Framework-by-Framework Architectural & Codebase Audit Report

**Date**: September 2026  
**Auditor**: Antigravity Assistant  
**Target Codebase**: `d:\Projects\miniverse`  
**Status**: Completed (Read-Only Audit Phase — No Source Code Modified)

---

## Executive Summary

A comprehensive architectural and code-level audit of Miniverse was performed across all 24 frameworks (F01–F24) and the Core Minigame & Routing substrate. The audit cross-referenced all active implementation code against the requirements, invariants, and decisions documented in `docs/status/ARCHITECTURE.md`, `docs/status/DECISIONS.md`, `docs/bedwars/`, and related documentation.

### High-Impact Vulnerabilities & Bugs Discovered:
1. **[CRITICAL] Permanent Null Pointer in Core Minigame Context (`AbstractMinigame.attachContext`)**:
   - `this.runtime` is permanently `null` in `AbstractMinigame` because `attachContext` is invoked during the constructor of `MinigameRuntime` *prior* to `MinigameManager.setActiveMinigame()` being assigned. Any gamemode invoking lifecycle methods like `MatchLifecycleController.endMatch(this.runtime, ...)` (such as `BedwarsMinigame.java:596, 602, 922`) crashes the server with an immediate unhandled `NullPointerException`.
2. **[CRITICAL] Shop GUI Item Drop & Duplication Exploit (F24 - `ShopGui`)**:
   - `ShopGui.onSlotClick` only intercepts `PICKUP` and cancels `QUICK_MOVE`/`SWAP`. Any `THROW` action (pressing the drop key 'Q' while hovering over shop items or the barrier exit button) falls through to vanilla's `super.onSlotClick()`, dropping unpurchased items or admin barrier blocks directly into the world.
3. **[CRITICAL] Broken Hotbar Layout Framework in Bedwars (F23 - `BedwarsDeathCallbacks`)**:
   - `BedwarsDeathCallbacks` calls `InventoryLayoutFramework.applyLayout()`, but fails to call `tagKitItem()` on the generated swords, pickaxes, and axes. As a result, layout mapping fails for all items, defaulting every item to slots 0–3, while `applyLayout()` indiscriminately calls `player.getInventory().clear()`, wiping the player's armor and inventory.
4. **[CRITICAL] Synchronous 20 Hz Disk Read & Gson Parse Bottleneck (F10 - `RuntimeMarkerCache`)**:
   - On every single server tick (50ms / 20 Hz), `RegionTriggerService.tick()` calls `RuntimeMarkerCache.getInstance().tick()`, which calls `SessionRuntimeConfig.getSessionJson()`, synchronously reading and parsing `session.json` from the physical disk on the primary server thread.
5. **[CRITICAL] Server-Client Desync on Instant Respawn (F05 - `TimedRespawnPolicy` & `DeathLifecycleManager`)**:
   - When a gamemode enables `doImmediateRespawn=true` with `DeathLifecycleManager`, the client immediately fires `PERFORM_RESPAWN`. Vanilla creates a new survival player at world spawn, bypassing the countdown spectator phase while the state machine believes the player is spectating.
6. **[HIGH] Port Collision in Server Launcher (F01 - `ServerLauncher`)**:
   - `ServerLauncher.reservePort` checks port availability via ephemeral socket binding, but maintains no in-flight reservations across concurrent or rapid session launches. Two concurrent launches can be assigned the exact same port.
7. **[HIGH] Permanent Freeze Desync (F03 - `FreezeService`)**:
   - `FreezeService.clearAll()` clears server-side state on match stop or reset without sending packet payloads to clients, leaving previously frozen players stuck in a client-side frozen state indefinitely.
8. **[HIGH] Scoreboard Objective Destruction & Client Flicker (F12 - `ScoreboardTemplate` & `BedwarsMinigame`)**:
   - Scoreboard line removals do not send score reset packets, and `BedwarsMinigame` invokes `resendStructure()` every second, recreating the objective on clients and causing severe visual flickering.
9. **[HIGH] Item Deletion in Drop Mixin (F13 - `ProtectedItemPlayerDropMixin`)**:
   - Canceling `PlayerEntity.dropItem` at `HEAD` returns `null`, but vanilla `dropSelectedItem` already removed the item from the player's slot, resulting in item deletion unless auto-restored.
10. **[HIGH] Piston Block Protection Desync (F09 - `PistonHandlerMixin`)**:
    - Piston pushes of player-placed blocks are allowed, but `MapProtectionTracker` coordinates are not updated, making the pushed block permanently protected while leaving a ghost tracker coordinate at the old location.

---

## Detailed Audit Findings by Framework

---

### Core Minigame & Event Router

#### Finding CORE-01: Permanent `null` in `AbstractMinigame.runtime` leading to server crash on match end
- **Severity**: `CRITICAL`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/AbstractMinigame.java:55`
  - `dev/frost/miniverse/minigame/core/MinigameRuntime.java:41`
  - `dev/frost/miniverse/minigame/impl/bedwars/BedwarsMinigame.java:596, 602, 922`
- **Why it is a Problem**:
  In `AbstractMinigame.attachContext(MinigameContext context)`, line 55 attempts to assign:
  ```java
  this.runtime = MinigameManager.getInstance().getRuntime();
  ```
  However, `minigame.attachContext(this.context)` is invoked directly inside the constructor of `MinigameRuntime(minigame, server, roster)`. At that exact moment, `MinigameManager.setActiveMinigame(minigame)` has not yet been executed, so `MinigameManager.getInstance().getRuntime()` returns `null`!
  Consequently, `this.runtime` in `AbstractMinigame` is permanently `null`. When a minigame implementation calls `MatchLifecycleController.endMatch(this.runtime, ...)` (e.g. in `BedwarsMinigame.java`), passing `this.runtime` passes `null`. `MatchLifecycleController.endMatch()` immediately dereferences `runtime.setState(GameState.ENDING)` without null checks, crashing the dedicated server with a fatal `NullPointerException`.
- **Recommended Fix**:
  1. Pass the `MinigameRuntime` instance directly into `attachContext(MinigameContext context, MinigameRuntime runtime)`:
     ```java
     public void attachRuntime(MinigameRuntime runtime) {
         this.runtime = runtime;
         this.context = runtime.context();
     }
     ```
  2. Or update `MinigameRuntime` constructor to assign the runtime reference to `AbstractMinigame`:
     ```java
     if (this.minigame instanceof AbstractMinigame abstractMinigame) {
         abstractMinigame.bindRuntime(this);
     }
     ```

---

#### Finding CORE-02: Player leave events suppressed during pause
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/event/MinigameEventRouter.java:264-266`
- **Why it is a Problem**:
  `MinigameEventRouter.onPlayerLeave()` checks:
  ```java
  if (this.pausedFor(player)) {
      return;
  }
  ```
  If a match is paused and a participant disconnects, this early return prevents `PlayerLeaveAware.onPlayerLeave()` and `DeathLifecycleManager.handleDisconnect()` from ever executing for that player. The player remains registered in the death state machine and game state, causing ghost player entries upon resume.
- **Recommended Fix**:
  Do not abort `onPlayerLeave` when paused. Instead, allow cleanup, death manager disconnection handling, and session state serialization to execute regardless of pause state.

---

### F01: Session Framework & Launcher

#### Finding F01-01: Port collision race condition in `ServerLauncher.reservePort`
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/session/ServerLauncher.java:185-199`
- **Why it is a Problem**:
  `ServerLauncher.reservePort()` tests port availability by creating a `ServerSocket(port)` and immediately closing it. However, it does not store the reserved port in an active in-memory set. If two sessions are launched concurrently or in rapid succession (e.g. via GUI or network packet), both threads will test the same port, find it available, and assign the identical port to two distinct backend Minecraft server processes. The second process will crash with `java.net.BindException: Address already in use`.
- **Recommended Fix**:
  Maintain a thread-safe `Set<Integer> ALLOCATED_PORTS = ConcurrentHashMap.newKeySet()` in `ServerLauncher`. Add the port to the set upon reservation, release it only if process launch fails or when the process terminates (`Process.onExit()`).

---

#### Finding F01-02: Child server process leak on host server crash
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/session/ServerLauncher.java:70-130`
- **Why it is a Problem**:
  Backend server processes are spawned using `ProcessBuilder`. If the main server crashes, is killed, or shuts down abruptly, these child processes continue running as orphaned processes holding memory and binding ports.
- **Recommended Fix**:
  Add a JVM shutdown hook (`Runtime.getRuntime().addShutdownHook(...)`) that iterates over active session processes in `SessionRegistry` and terminates child processes (`process.destroyForcibly()`). Additionally, backend servers should implement an IPC heartbeat or parent process liveness check.

---

### F02: Match Lifecycle Framework

#### Finding F02-01: Re-entrant `endMatch` invocation and duplicate end sequence scheduling
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/lifecycle/MatchLifecycleController.java:50-80`
- **Why it is a Problem**:
  `endMatch()` is not atomic or synchronized. If multiple victory conditions trigger on the exact same tick (e.g. bed break elimination and a simultaneous final kill), `endMatch()` can execute concurrently or twice in the same tick. This results in two `StandardEndSequence` tasks running simultaneously, duplicate chat announcements, and duplicate teleport timers.
- **Recommended Fix**:
  Guard `endMatch` with an atomic state check:
  ```java
  if (!runtime.compareAndSetState(GameState.RUNNING, GameState.ENDING)) {
      return;
  }
  ```

---

### F03: Freeze Framework

#### Finding F03-01: Client input freeze permanently desynced on match stop/reset
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/freeze/FreezeService.java:65-75`
- **Why it is a Problem**:
  `FreezeService.clearAll()` clears the server's internal `frozenPlayers` map. However, it fails to send `NetworkConstants.FreezeStatePayload(false)` to the clients of those players!
  If players are frozen (e.g. during countdown or match pause) and the match abruptly stops or resets, their client-side `FreezeClientReceiver` remains in `frozen = true` mode. The players remain completely unable to move their camera or WASD until they reconnect or a new game freezes and unfreezes them.
- **Recommended Fix**:
  Iterate over all currently frozen players before clearing the map and send the unfreeze packet:
  ```java
  for (UUID uuid : this.frozenPlayers.keySet()) {
      ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
      if (player != null) {
          ServerPlayNetworking.send(player, new NetworkConstants.FreezeStatePayload(false));
      }
  }
  this.frozenPlayers.clear();
  ```

---

### F04: Spectator Framework

#### Finding F04-01: Spectator arena boundary clipping and chunk loading
- **Severity**: `LOW`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/spectator/SpectatorService.java:35-50`
- **Why it is a Problem**:
  `SpectatorService.makeSpectator()` places players into vanilla `GameMode.SPECTATOR`. In vanilla spectator mode, players can fly through blocks with unlimited speed, clip outside arena boundaries, explore ungenerated chunks, or observe opponent team bases. No boundary checks or position tethering are enforced.
- **Recommended Fix**:
  Enforce bounding box constraints during spectator tick in `SpectatorService`: if a spectator's position leaves the configured arena boundary, clamp their position or teleport them back to the arena spectator spawn point.

---

### F05: Death Lifecycle Framework

#### Finding F05-01: Client-Server race condition on `doImmediateRespawn=true`
- **Severity**: `CRITICAL`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/death/TimedRespawnPolicy.java:50-70`
  - `dev/frost/miniverse/minigame/core/death/DeathLifecycleManager.java:80-110`
- **Why it is a Problem**:
  When a minigame sets `doImmediateRespawn = true` in gamerules while using `DeathLifecycleManager` with `interceptsRespawn = true`, the client bypasses the vanilla death screen and immediately sends `ClientStatusC2SPacket.PERFORM_RESPAWN`.
  Vanilla handles this packet by immediately creating a new `ServerPlayerEntity` in `GameMode.SURVIVAL` at world spawn. However, `DeathLifecycleManager` still tracks the player in `DeathState.SPECTATING` with a countdown timer. When the timer expires, it tries to respawn a player who has already been respawned in survival, corrupting the death state machine and teleporting the player unexpectedly.
- **Recommended Fix**:
  Align with Decision `D04`: If `DeathLifecycleManager` handles timed respawns, `doImmediateRespawn` must either be forced `false` on the server gamerules, or `DeathLifecycleManager.handleVanillaRespawn()` must detect if a player is in `DeathState.SPECTATING` and immediately enforce spectator gamemode and invisibility on the newly created player entity until the timer elapses.

---

### F08: Team Framework & Chat Routing

#### Finding F08-01: Scoreboard team leak across session restarts
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/team/VanillaTeamAdapter.java:40-65`
- **Why it is a Problem**:
  `VanillaTeamAdapter` creates vanilla `Team` entries on the server's primary `Scoreboard`. If a match terminates or resets without explicitly unregistering these teams, team prefixes, suffixes, and collision settings persist on the scoreboard. When a subsequent minigame session starts, stale teams collide with new team definitions.
- **Recommended Fix**:
  Implement `VanillaTeamAdapter.unregisterAll()` and ensure it is called in `StandardEndSequence` and `MinigameRuntime.stop()`.

---

### F09: Map Protection Framework

#### Finding F09-01: Piston pushing breaks block tracking and creates indestructible blocks
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/mixin/protection/PistonHandlerMixin.java:25-45`
  - `dev/frost/miniverse/minigame/core/protection/MapProtectionTracker.java`
- **Why it is a Problem**:
  `PistonHandlerMixin` allows pistons to push player-placed blocks. However, when the block moves from position `A` to position `B`, `MapProtectionTracker` is not updated. As a result:
  - Position `A` (now air) is still recorded as player-placed.
  - Position `B` (the placed block's new position) is NOT recorded in `MapProtectionTracker`. Under default map protection rules, position `B` is now treated as part of the natural map and becomes completely unbreakable by players!
- **Recommended Fix**:
  Inject into `PistonBlockEntity.finish()` or `PistonHandler.calculatePush()` to update `MapProtectionTracker`: remove source positions and add destination positions to the player-placed block set.

---

#### Finding F09-02: Explosion block destruction leaks coordinates in `MapProtectionTracker`
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/mixin/protection/ExplosionMixin.java`
- **Why it is a Problem**:
  When an explosion destroys player-placed blocks, `MapProtectionTracker` does not remove those coordinates. Over lengthy matches involving heavy TNT or fireball usage (e.g. Bedwars), the tracker accumulates thousands of stale block positions, wasting memory and degrading lookup performance.
- **Recommended Fix**:
  Inject into `Explosion.affectWorld()` to remove all destroyed blocks from `MapProtectionTracker`.

---

### F10: Region Trigger Framework & Marker Cache

#### Finding F10-01: Synchronous 20 Hz disk I/O and JSON parsing on the main server thread
- **Severity**: `CRITICAL`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/region/RegionTriggerService.java:45-50`
  - `dev/frost/miniverse/minigame/core/region/RuntimeMarkerCache.java:60-75`
  - `dev/frost/miniverse/session/SessionRuntimeConfig.java:35-45`
- **Why it is a Problem**:
  `RegionTriggerService.tick()` is registered to `ServerTickEvents.END_SERVER_TICK` (executing 20 times per second). On every tick, it calls:
  ```java
  RuntimeMarkerCache.getInstance().tick();
  ```
  `RuntimeMarkerCache.tick()` invokes `resolveIndexConfig()`, which calls:
  ```java
  SessionRuntimeConfig.getSessionJson();
  ```
  `SessionRuntimeConfig.getSessionJson()` performs synchronous file I/O: it opens `session.json` using `Files.newBufferedReader()`, parses the entire JSON structure with `Gson`, and returns it.
  **This causes synchronous disk file reads and JSON deserialization 20 times every second on the primary Minecraft server thread**, introducing severe micro-stuttering and tanking server TPS.
- **Recommended Fix**:
  Cache the parsed `JsonObject` in `SessionRuntimeConfig` in memory. Reload the config file only when explicitly modified or invalidated by a file watcher / reload packet, never on every tick.

---

#### Finding F10-02: O(P * R) region check on every tick
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/region/RegionTriggerService.java:80-110`
- **Why it is a Problem**:
  For every online player, `RegionTriggerService` linearly iterates through every registered region boundary box on every tick. If an arena contains 40 regions (spawners, shops, killzones, team zones) and 16 players, 640 bounding box checks occur every 50ms.
- **Recommended Fix**:
  Partition regions into spatial chunk maps (`Long2ObjectOpenHashMap<List<Region>>`). Only test regions that intersect the chunk currently occupied by the player.

---

### F11: Map Editor Framework

#### Finding F11-01: Synchronous file writes during marker placement
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/map/editor/MapEditorMarkerStore.java:80-110`
- **Why it is a Problem**:
  When an admin adds or edits a marker via the Map Editor tool, `MapEditorMarkerStore.save()` immediately writes the entire marker database to disk synchronously on the main thread.
- **Recommended Fix**:
  Perform marker file writes asynchronously via `CompletableFuture.runAsync()` or an `ExecutorService`.

---

### F12: Scoreboard Framework

#### Finding F12-01: Scoreboard line clearing fails to send reset packets to clients
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/scoreboard/ScoreboardTemplate.java:70-85`
- **Why it is a Problem**:
  `ScoreboardTemplate.clearLines()` clears its internal server list of lines. However, it never sends `ScoreboardScoreResetS2CPacket` for the cleared scores to client connections. Lines that are removed on the server remain visible on the player's client scoreboard.
- **Recommended Fix**:
  Send `ResetScoreS2CPacket` (or set score to 0 / remove score) for every cleared line score to all viewers before clearing the internal line collection.

---

#### Finding F12-02: Scoreboard objective destruction every second causing severe visual flicker
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/impl/bedwars/BedwarsMinigame.java:620-635`
- **Why it is a Problem**:
  In `BedwarsMinigame`, the scoreboard tick calls `board.resendStructure()` every 20 ticks. This packet sends an objective remove packet followed by an objective add packet. On the client, this causes the scoreboard HUD to disappear and reappear every second, resulting in intolerable visual flickering and excessive network packet overhead.
- **Recommended Fix**:
  Never destroy and recreate the scoreboard objective. Update line text in-place using team prefix/suffix styling or score value updates without toggling the objective.

---

### F13: Protected Items Framework

#### Finding F13-01: Item deletion bug in `ProtectedItemPlayerDropMixin`
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/mixin/item/ProtectedItemPlayerDropMixin.java:20-35`
- **Why it is a Problem**:
  `ProtectedItemPlayerDropMixin` intercepts `PlayerEntity.dropItem(ItemStack, boolean, boolean)` at `HEAD` and cancels it if the item is protected, returning `null`.
  However, vanilla `PlayerInventory.dropSelectedItem(boolean)` extracts the item from the slot *before* calling `dropItem()`. Because the mixin cancels the drop and returns `null` without placing the item back into the player's inventory slot, the item is permanently lost from the inventory!
- **Recommended Fix**:
  In the mixin, re-insert the canceled item back into the player's inventory:
  ```java
  player.getInventory().offerOrDrop(stack);
  ci.setReturnValue(null);
  ```

---

### F14: Kit Framework

#### Finding F14-01: Unvalidated NBT size in custom kit creation
- **Severity**: `LOW`
- **Affected Files**:
  - `dev/frost/miniverse/network/handlers/KitNetworkHandler.java:36-58`
- **Why it is a Problem**:
  `handleCreateKit` captures all items directly from player inventory into kit JSON files without checking total NBT payload size. Malformed or oversized items can lead to multi-megabyte kit files and disk write hangs.
- **Recommended Fix**:
  Validate item stack counts and component byte sizes before serializing custom kits.

---

### F15, F16, F17: Role, Visibility & Corpse Frameworks

#### Finding F17-01: Unbounded corpse entity accumulation
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/corpse/CorpseManager.java:50-80`
- **Why it is a Problem**:
  Corpses spawned upon death do not enforce an absolute cap per player or arena. In fast-paced games with repeated deaths, accumulated corpse entities cause client-side rendering lag and memory pressure.
- **Recommended Fix**:
  Enforce a FIFO cap (e.g. maximum 3 corpses per player, 20 per arena) and automatically despawn the oldest corpse when the limit is exceeded.

---

### F18: Arena Framework

#### Finding F18-01: World-wide entity iteration during arena reset
- **Severity**: `MEDIUM`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/arena/Arena.java:90-110`
- **Why it is a Problem**:
  `Arena.startReset()` clears entities by calling `world.iterateEntities()`, which iterates across **every entity in the entire Minecraft world**. On a server hosting multiple minigame arenas or persistent worlds, this is an $O(N_{\text{world}})$ operation that destroys entities outside the arena boundary and causes tick lag.
- **Recommended Fix**:
  Query entities strictly within the arena bounding box:
  ```java
  List<Entity> arenaEntities = world.getOtherEntities(null, this.bounds, e -> !(e instanceof PlayerEntity));
  arenaEntities.forEach(Entity::discard);
  ```

---

### F19: Countdown Service

#### Finding F19-01: Ghost sound and title packet broadcasts to disconnected players
- **Severity**: `LOW`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/countdown/CountdownService.java:60-90`
- **Why it is a Problem**:
  `CountdownService` iterates over participant UUIDs without filtering out disconnected players, producing failed packet logging warnings in console.
- **Recommended Fix**:
  Check `player != null && !player.isDisconnected()` before sending title and sound packets.

---

### F20: Player State Snapshot & Persistence Framework

#### Finding F20-01: Inventory item leakage on snapshot restoration
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/persistence/PlayerStateSnapshot.java:161-170`
- **Why it is a Problem**:
  `PlayerStateSnapshot.restoreInventory()` parses SNBT and calls `player.getInventory().readNbt(inventory)`.
  In Minecraft 1.21+, `readNbt(NbtList)` only overwrites slots present in the NBT list. **It does NOT clear empty slots!**
  If a player acquired items during a minigame (such as gold, diamonds, or kit items) in slots that were empty when the initial snapshot was captured, those items are NOT cleared when restoring the snapshot. The player keeps the minigame items in their survival inventory!
- **Recommended Fix**:
  Explicitly clear the player's inventory before calling `readNbt`:
  ```java
  player.getInventory().clear();
  player.getInventory().readNbt(inventory);
  player.getInventory().markDirty();
  player.currentScreenHandler.sendContentUpdates();
  ```

---

#### Finding F20-02: Duplicate enum check in `PlayerStateStore`
- **Severity**: `LOW`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/persistence/PlayerStateStore.java:138-139`
- **Why it is a Problem**:
  `state == GameState.RUNNING` is checked twice consecutively in `shouldPreservePreviousWithoutRoster()`.
- **Recommended Fix**:
  Remove the duplicate condition.

---

### F21: Derangement Framework

#### Finding F21-01: Non-derangement fallback in `DerangementAssignment.cycleDerangement`
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/swap/DerangementAssignment.java:80-89`
- **Why it is a Problem**:
  `cycleDerangement(sources)` is the guaranteed fallback when random derangement attempts fail. It executes:
  ```java
  List<T> targets = new ArrayList<>(sources);
  Collections.shuffle(targets, ThreadLocalRandom.current());
  Collections.rotate(targets, 1);
  ```
  If `targets` is shuffled first and then rotated by 1, it is **NOT** mathematically guaranteed to be a derangement. For example, if `sources = [A, B, C]` and the shuffle randomly produces `[B, C, A]`, rotating right by 1 produces `[A, B, C]`. Then `sources.get(0) == targets.get(0) == A`!
  A player can be assigned to swap with themselves, breaking game mechanics in derangement-based minigames.
- **Recommended Fix**:
  Rotate directly from `sources` without an independent random shuffle:
  ```java
  List<T> targets = new ArrayList<>(sources);
  Collections.rotate(targets, 1);
  ```
  Or shuffle `sources` first, and set `targets.set(i, sources.get((i + 1) % n))`.

---

### F23: Inventory Layout Framework

#### Finding F23-01: Bedwars hotbar layout completely non-functional due to missing item tags
- **Severity**: `CRITICAL`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/impl/bedwars/death/BedwarsDeathCallbacks.java:100-115`
  - `dev/frost/miniverse/minigame/core/layout/InventoryLayoutFramework.java:45-65`
- **Why it is a Problem**:
  In `BedwarsDeathCallbacks.java:100-115`, `itemsToGive` is populated with `WOODEN_SWORD`, pickaxes, axes, and stick. Then line 115 calls:
  ```java
  InventoryLayoutFramework.applyLayout(player, BedwarsDefinition.ID, itemsToGive);
  ```
  However, **none of these items are tagged with `tagKitItem()`**!
  When `applyLayout` processes each item, `getKitItemId(item)` returns `null`. None of the items match any configured layout keys. Every single item is treated as unmapped and placed in sequential hotbar slots 0, 1, 2...
  The Bedwars custom hotbar layout feature does not work at all.
- **Recommended Fix**:
  Tag the items in `BedwarsDeathCallbacks` prior to passing them to `applyLayout`:
  ```java
  InventoryLayoutFramework.tagKitItem(sword, "BEDWARS_SWORD");
  InventoryLayoutFramework.tagKitItem(pickaxe, "BEDWARS_PICKAXE");
  InventoryLayoutFramework.tagKitItem(axe, "BEDWARS_AXE");
  ```

---

#### Finding F23-02: Indiscriminate `player.getInventory().clear()` destroys armor and offhand
- **Severity**: `HIGH`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/layout/InventoryLayoutFramework.java:68`
- **Why it is a Problem**:
  `InventoryLayoutFramework.applyLayout` executes:
  ```java
  player.getInventory().clear();
  ```
  This clears the player's ENTIRE inventory, including armor slots (36–39) and offhand (40). If a gamemode equips armor before applying layout, or if layout is applied mid-match, the player's armor and non-kit items are wiped out.
- **Recommended Fix**:
  Only clear hotbar slots (0–8) and main inventory slots (9–35), preserving armor and offhand slots.

---

### F24: Shop Framework

#### Finding F24-01: Shop GUI item theft & duplication exploit via `THROW` / `CLONE`
- **Severity**: `CRITICAL`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/shop/ShopGui.java:28-67`
- **Why it is a Problem**:
  In `ShopGui.open()`, the custom container handler overrides `onSlotClick`:
  ```java
  if (slotIndex >= 0 && slotIndex < 54) {
      if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
          // handles purchase or category click
      } else if (actionType == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE || actionType == net.minecraft.screen.slot.SlotActionType.SWAP) {
          this.sendContentUpdates();
      } else {
          super.onSlotClick(slotIndex, button, actionType, p2);
      }
  }
  ```
  Notice that if `actionType` is `THROW` (the drop key 'Q'), `CLONE` (creative middle-click), or `PICKUP_ALL` (double click):
  **It executes `super.onSlotClick(slotIndex, button, actionType, p2)`!**
  In vanilla Minecraft, `super.onSlotClick` with `THROW` drops the ItemStack in the slot directly into the world!
  - Hovering over any shop item and pressing 'Q' drops that item onto the ground for free without paying any currency.
  - Hovering over slot 53 and pressing 'Q' drops the `Items.BARRIER` block into the world, giving players illegal barrier blocks.
- **Recommended Fix**:
  Explicitly cancel and consume all slot click actions on top inventory slots (`0 <= slotIndex < 54`) that are not valid `PICKUP` interactions:
  ```java
  if (slotIndex >= 0 && slotIndex < 54) {
      if (actionType != net.minecraft.screen.slot.SlotActionType.PICKUP) {
          this.sendContentUpdates();
          return;
      }
      // handle valid clicks...
  }
  ```

---

#### Finding F24-02: Missing screen content sync on successful purchase
- **Severity**: `LOW`
- **Affected Files**:
  - `dev/frost/miniverse/minigame/core/shop/ShopGui.java:47`
- **Why it is a Problem**:
  When a purchase succeeds, line 47 executes:
  ```java
  updateInventory(inventory, (ServerPlayerEntity) p2, categories, this.activeCategory);
  return;
  ```
  Because it returns immediately, line 61 (`this.sendContentUpdates()`) is never called, which can cause client GUI desynchronization (such as lingering cursor item or outdated currency count).
- **Recommended Fix**:
  Call `this.sendContentUpdates()` before returning.

---

## Comprehensive Framework Findings Matrix

| Framework | Finding ID | Severity | Problem Summary | Recommended Action |
|---|---|---|---|---|
| **Core** | CORE-01 | `CRITICAL` | `AbstractMinigame.runtime` is permanently `null` | Pass runtime into `attachContext()` or bind in `MinigameRuntime` constructor |
| **Core** | CORE-02 | `MEDIUM` | `onPlayerLeave` ignored when match is paused | Remove pause check in `MinigameEventRouter.onPlayerLeave` |
| **F01** | F01-01 | `HIGH` | Port allocation race condition in `reservePort` | Track in-flight reserved ports in thread-safe memory set |
| **F01** | F02-02 | `MEDIUM` | Child server process leak on host crash | Register JVM shutdown hook to terminate child processes |
| **F02** | F02-01 | `MEDIUM` | Non-atomic `endMatch` invocation | Guard `endMatch` with atomic state transition check |
| **F03** | F03-01 | `HIGH` | `FreezeService.clearAll()` leaves client frozen | Send `FreezeStatePayload(false)` to players before clearing map |
| **F04** | F04-01 | `LOW` | Spectators can fly through blocks and leave arena | Clamp spectator positions within arena bounding box |
| **F05** | F05-01 | `CRITICAL` | Desync between `doImmediateRespawn` & death state machine | Align with D04; enforce spectator state or disable immediate respawn |
| **F08** | F08-01 | `MEDIUM` | Vanilla scoreboard teams leak across session restarts | Unregister scoreboard teams on match termination |
| **F09** | F09-01 | `HIGH` | Piston pushing breaks block tracking; creates unbreakable blocks | Intercept piston movements and update `MapProtectionTracker` |
| **F09** | F09-02 | `MEDIUM` | Explosions leak coordinates in `MapProtectionTracker` | Prune destroyed blocks from tracker in explosion mixin |
| **F10** | F10-01 | `CRITICAL` | Synchronous 20 Hz disk read & Gson parse in `RuntimeMarkerCache` | Cache parsed session JSON in memory; do not read disk on every tick |
| **F10** | F10-02 | `MEDIUM` | O(P * R) region scan on every tick | Spatial partition regions by chunk coordinates |
| **F11** | F11-01 | `MEDIUM` | Map editor saves markers synchronously on main thread | Write marker JSON files asynchronously |
| **F12** | F12-01 | `HIGH` | `ScoreboardTemplate.clearLines` leaves ghost lines on client | Send score reset packets to clients when lines are cleared |
| **F12** | F12-02 | `HIGH` | `BedwarsMinigame` recreates scoreboard objective every second | Update lines in-place without toggling scoreboard objective |
| **F13** | F13-01 | `HIGH` | Canceled drop in `ProtectedItemPlayerDropMixin` deletes items | Re-insert item into player inventory upon drop cancel |
| **F14** | F14-01 | `LOW` | Unvalidated NBT size in custom kit creation | Validate byte and component limits before serializing kit JSON |
| **F17** | F17-01 | `MEDIUM` | Corpse entity count is unbounded | Cap corpses per player / arena with FIFO cleanup |
| **F18** | F18-01 | `MEDIUM` | Arena reset iterates all entities across the entire world | Query entities strictly inside the arena bounding box |
| **F19** | F19-01 | `LOW` | Countdown broadcasts to disconnected players | Validate connection status before sending packets |
| **F20** | F20-01 | `HIGH` | `PlayerStateSnapshot.restore()` leaves minigame items in inventory | Clear player inventory before calling `readNbt()` |
| **F20** | F20-02 | `LOW` | Duplicate `GameState.RUNNING` check | Remove redundant check |
| **F21** | F21-01 | `HIGH` | `cycleDerangement` shuffle + rotate is not a derangement | Rotate directly from sources without independent shuffle |
| **F23** | F23-01 | `CRITICAL` | Bedwars hotbar layout fails; items never tagged with kit IDs | Call `tagKitItem()` on all given tools and weapons |
| **F23** | F23-02 | `HIGH` | `applyLayout` clears entire inventory including armor | Restrict inventory clearing to hotbar and main storage slots |
| **F24** | F24-01 | `CRITICAL` | Shop GUI allows dropping/duplicating items via 'Q' key | Disallow non-PICKUP actions on shop container slots |
| **F24** | F24-02 | `LOW` | Missing `sendContentUpdates` after purchase | Sync container contents immediately after purchase |

---

## Recommended Next Steps

1. **Review Findings**: Review the findings and severity rankings above.
2. **Prioritization**:
   - **Phase 1 (Critical & High-Severity Fixes)**: Resolve CORE-01, F24-01 (Shop exploit), F23-01 & F23-02 (Layout & Item deletion), F10-01 (20 Hz Disk Read), F05-01 (Respawn desync), and F03-01 (Permanent freeze).
   - **Phase 2 (Lifecycle & Performance Fixes)**: Resolve F01-01 (Port collision), F09-01 (Piston protection), F12-01 & F12-02 (Scoreboard flicker), F18-01 (Arena entity scan), and F20-01 (Inventory restore).
   - **Phase 3 (Cleanup & Edge Cases)**: Resolve remaining medium and low severity items.
