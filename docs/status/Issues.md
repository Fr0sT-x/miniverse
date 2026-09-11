1. Core Minigame Framework & Event Routing
[CRITICAL] AbstractMinigame.runtime Is Permanently null
Affected Files/Classes:


AbstractMinigame.java:55


MinigameRuntime.java:41


BedwarsMinigame.java:596, 602, 922
Why it is a Problem: AbstractMinigame.attachContext calls this.runtime = MinigameManager.getInstance().getRuntime(). However, attachContext() is executed inside the constructor of 

MinigameRuntime
 before MinigameManager.setActiveMinigame(...) is ever called. As a result, this.runtime in AbstractMinigame is permanently null. Any minigame calling MatchLifecycleController.endMatch(this.runtime, ...) immediately throws a fatal NullPointerException on runtime.setState(GameState.ENDING) and crashes the server.
Recommended Fix: Bind the runtime directly by passing it into attachContext(MinigameContext, MinigameRuntime) or setting ((AbstractMinigame) minigame).bindRuntime(this) inside the MinigameRuntime constructor.
[MEDIUM] Player Disconnect Events Suppressed When Match Is Paused
Affected Files/Classes:


MinigameEventRouter.java:264-266
Why it is a Problem: onPlayerLeave() checks if (this.pausedFor(player)) return;. If a player leaves while the game is paused, PlayerLeaveAware.onPlayerLeave() and 

DeathLifecycleManager.handleDisconnect()
 never run for that player.
Recommended Fix: Allow leave notifications and disconnect cleanup handlers to execute regardless of pause state.
2. F01: Session Framework & Launcher
[HIGH] Port Collision Race Condition in Port Reservation
Affected Files/Classes:


ServerLauncher.java:185-199
Why it is a Problem: reservePort() tests port availability with an ephemeral ServerSocket(port) bind/close, but does not maintain an in-memory set of reserved ports. If two sessions launch concurrently or in rapid succession, both threads find the same port available and assign it to two different backend processes. The second process fails with java.net.BindException: Address already in use.
Recommended Fix: Track active and in-flight allocated ports in a thread-safe Set<Integer> ALLOCATED_PORTS = ConcurrentHashMap.newKeySet(). Release the port only when the session process terminates (Process.onExit()).
[MEDIUM] Child Backend Server Leak on Host Server Termination
Affected Files/Classes:


ServerLauncher.java:70-130
Why it is a Problem: Backend servers are spawned via ProcessBuilder. If the main server crashes or is abruptly killed, child JVM processes continue running as orphaned processes holding memory and listening on ports.
Recommended Fix: Register a JVM shutdown hook (Runtime.getRuntime().addShutdownHook(...)) that terminates all active child processes in 

SessionRegistry
.
3. F02: Match Lifecycle Framework
[MEDIUM] Re-entrant endMatch Invocations Cause Duplicate End Sequences
Affected Files/Classes:


MatchLifecycleController.java:50-80
Why it is a Problem: If multiple elimination or victory triggers fire on the exact same tick, endMatch() can execute twice, scheduling duplicate 

StandardEndSequence
 tasks and sending conflicting title/chat announcements.
Recommended Fix: Use an atomic state transition check: if (!runtime.compareAndSetState(GameState.RUNNING, GameState.ENDING)) return;.
4. F03: Freeze Framework
[HIGH] Client Input Freeze Desync on Match Stop/Reset
Affected Files/Classes:


FreezeService.java:65-75
Why it is a Problem: FreezeService.clearAll() clears the internal frozenPlayers map, but fails to send NetworkConstants.FreezeStatePayload(false) to clients. Any player who was frozen when a match terminates or resets remains permanently client-side input-frozen until they reconnect.
Recommended Fix: Iterate through all frozen players in clearAll() and send FreezeStatePayload(false) prior to clearing the map.
5. F04: Spectator Framework
[LOW] Spectators Free to Clip Through World & Arena Boundaries
Affected Files/Classes:


SpectatorService.java:35-50
Why it is a Problem: Players in vanilla GameMode.SPECTATOR can noclip through arena barriers, scout secret areas, fly into unloaded chunks, or enter opposing team bases without restriction.
Recommended Fix: Enforce arena bounding box containment during spectator ticks; tether or teleport violators back to the spectator spawn.
6. F05: Death Lifecycle Framework
[CRITICAL] Severe Desync Between doImmediateRespawn and Death State Machine
Affected Files/Classes:


TimedRespawnPolicy.java:50-70


DeathLifecycleManager.java:80-110
Why it is a Problem: When a minigame configures doImmediateRespawn = true while using DeathLifecycleManager, the client immediately sends PERFORM_RESPAWN. Vanilla creates a new survival player at world spawn, bypassing the timed spectator countdown, while the state machine still tracks the player as spectating.
Recommended Fix: Strictly enforce Decision D04: If DeathLifecycleManager handles timed respawns, force doImmediateRespawn = false in gamerules, or intercept vanilla respawns to re-impose spectator mode until the countdown timer expires.
7. F08: Team Framework & Chat Routing
[MEDIUM] Vanilla Scoreboard Teams Leak Across Sessions
Affected Files/Classes:


VanillaTeamAdapter.java:40-65
Why it is a Problem: VanillaTeamAdapter mutates vanilla scoreboard teams without cleanup on session termination, causing team names and prefixes to leak and collide across subsequent sessions.
Recommended Fix: Implement VanillaTeamAdapter.unregisterAll() and ensure it is invoked on match stop and during StandardEndSequence.
8. F09: Map Protection Framework
[HIGH] Piston Block Movement Corrupts Protection Tracking
Affected Files/Classes:


PistonHandlerMixin.java:25-45


MapProtectionTracker.java
Why it is a Problem: When pistons push player-placed blocks, MapProtectionTracker is not updated with the new block coordinates. The pushed block becomes natural map terrain (indestructible), while the old air coordinate remains tracked as player-placed.
Recommended Fix: Intercept piston movement completion (PistonBlockEntity.finish()) to remove the source coordinates and add destination coordinates to MapProtectionTracker.
[MEDIUM] Explosion Destruction Leaks Tracker Coordinates
Affected Files/Classes:


ExplosionMixin.java
Why it is a Problem: Explosions destroy player blocks without pruning them from MapProtectionTracker, causing memory leaks during prolonged matches with TNT and fireballs.
Recommended Fix: Remove destroyed blocks from MapProtectionTracker in Explosion.affectWorld().
9. F10: Region Trigger Framework & RuntimeMarkerCache
[CRITICAL] Synchronous 20 Hz Disk Read & Gson Parse on Main Server Thread
Affected Files/Classes:


RegionTriggerService.java:45-50


RuntimeMarkerCache.java:60-75


SessionRuntimeConfig.java:35-45
Why it is a Problem: RegionTriggerService.tick() runs every 50ms (20 Hz) and calls RuntimeMarkerCache.getInstance().tick(). This invokes SessionRuntimeConfig.getSessionJson(), which synchronously reads session.json from disk and deserializes it with Gson 20 times every second directly on the Minecraft server thread, causing severe micro-stuttering and tanking TPS.
Recommended Fix: Cache session.json in memory inside SessionRuntimeConfig. Reload it only on explicit reload events or file watch triggers, never on every tick.
[MEDIUM] $O(P \times R)$ Region Evaluation Every Tick
Affected Files/Classes:


RegionTriggerService.java:80-110
Why it is a Problem: Every player is tested against every registered region on every tick without spatial partitioning.
Recommended Fix: Index regions by chunk coordinates in a Long2ObjectOpenHashMap<List<Region>> and only test regions intersecting the player's current chunk.
10. F11: Map Editor Framework
[MEDIUM] Synchronous Disk Write During Marker Operations
Affected Files/Classes:


MapEditorMarkerStore.java:80-110
Why it is a Problem: Saving markers performs blocking JSON serialization directly on the server thread during player edits.
Recommended Fix: Offload marker file writes to an asynchronous worker thread.
11. F12: Scoreboard Framework
[HIGH] ScoreboardTemplate.clearLines Leaves Ghost Scores on Clients
Affected Files/Classes:


ScoreboardTemplate.java:70-85
Why it is a Problem: Clearing lines clears the internal server list but never dispatches ScoreboardScoreResetS2CPacket to viewers, leaving deleted lines visible on client HUDs.
Recommended Fix: Dispatch score reset packets for all removed lines to active viewers before clearing the collection.
[HIGH] Scoreboard Objective Recreated Every Second Causing Screen Flickering
Affected Files/Classes:


BedwarsMinigame.java:620-635
Why it is a Problem: BedwarsMinigame invokes board.resendStructure() every second (20 ticks), destroying and recreating the objective and producing jarring visual HUD flickering and packet spam.
Recommended Fix: Update score line text and numbers in-place without destroying and recreating the objective.
12. F13: Protected Items Framework
[HIGH] Item Deletion Bug in ProtectedItemPlayerDropMixin
Affected Files/Classes:


ProtectedItemPlayerDropMixin.java:20-35
Why it is a Problem: Vanilla dropSelectedItem extracts the item from the inventory slot before calling dropItem(). When the mixin intercepts dropItem() at HEAD and cancels it, it returns null without returning the item to the player's inventory, permanently destroying the item.
Recommended Fix: Re-insert the canceled item back into the player's inventory (player.getInventory().offerOrDrop(stack)) before returning.
13. F14: Kit Framework
[LOW] Unbounded NBT Payload Size During Kit Creation
Affected Files/Classes:


KitNetworkHandler.java:36-58
Why it is a Problem: Custom kit creation serializes raw inventory NBT components without size limits, allowing oversized payloads to lag file I/O.
Recommended Fix: Validate item component limits and payload byte sizes prior to kit serialization.
14. F17: Corpse Framework
[MEDIUM] Corpse Entity Accumulation Without Cap
Affected Files/Classes:


CorpseManager.java:50-80
Why it is a Problem: In high-elimination minigames, corpse entities accumulate indefinitely without an arena or per-player ceiling, degrading client rendering FPS.
Recommended Fix: Enforce a FIFO cap (e.g. max 3 corpses per player, max 20 per arena).
15. F18: Arena Framework
[MEDIUM] World-Wide Entity Iteration on Arena Reset
Affected Files/Classes:


Arena.java:90-110
Why it is a Problem: Arena.startReset() calls world.iterateEntities(), iterating over every entity in the entire world, discarding non-player entities outside the arena boundary and causing unnecessary lag spikes.
Recommended Fix: Query entities strictly within the arena bounding box via world.getOtherEntities(null, this.bounds, e -> !(e instanceof PlayerEntity)).
16. F19: Countdown Service
[LOW] Ghost Packet Broadcasts to Disconnected Players
Affected Files/Classes:


CountdownService.java:60-90
Why it is a Problem: Packets are sent to participant UUIDs without validating if the player entity is still online, producing console warnings.
Recommended Fix: Filter for player != null && !player.isDisconnected() before sending packets.
17. F20: Player State Snapshot & Persistence Framework
[HIGH] Inventory Item Leakage on Snapshot Restoration
Affected Files/Classes:


PlayerStateSnapshot.java:161-170
Why it is a Problem: readNbt(NbtList) only overwrites slots defined in the NBT list and does not clear empty slots. If a player acquired items during a minigame in slots that were empty before the game started, those items remain in the player's inventory when returning to survival.
Recommended Fix: Call player.getInventory().clear() prior to invoking readNbt().
[LOW] Duplicate Enum Check in PlayerStateStore
Affected Files/Classes:


PlayerStateStore.java:138-139
Why it is a Problem: state == GameState.RUNNING is checked twice consecutively in shouldPreservePreviousWithoutRoster().
Recommended Fix: Remove the duplicate check.
18. F21: Derangement Framework
[HIGH] Fallback In cycleDerangement Violates Derangement Guarantee
Affected Files/Classes:


DerangementAssignment.java:80-89
Why it is a Problem: cycleDerangement shuffles targets first, then rotates by 1. If the shuffle randomly outputs a permutation where targets[i] == sources[(i - 1 + n) % n], rotating by 1 yields targets[i] == sources[i], assigning players to themselves.
Recommended Fix: Rotate directly from sources without a prior independent shuffle, or set targets[i] = sources[(i + 1) % n].
19. F23: Inventory Layout Framework
[CRITICAL] Bedwars Hotbar Layout Non-Functional (Missing Kit Item Tags)
Affected Files/Classes:


BedwarsDeathCallbacks.java:100-115


InventoryLayoutFramework.java:45-65
Why it is a Problem: BedwarsDeathCallbacks calls applyLayout(), but never calls tagKitItem() on the wooden sword, pickaxe, axe, or knockback stick. getKitItemId() returns null for every item, and the layout framework defaults all items to slots 0–3, completely breaking custom hotbar preferences.
Recommended Fix: Tag all Bedwars kit items with their respective IDs (BEDWARS_SWORD, BEDWARS_PICKAXE, etc.) before calling applyLayout().
[HIGH] applyLayout Indiscriminately Wipes Armor & Offhand
Affected Files/Classes:


InventoryLayoutFramework.java:68
Why it is a Problem: player.getInventory().clear() clears the entire inventory including armor (slots 36–39) and offhand (slot 40).
Recommended Fix: Only clear hotbar slots (0–8) and main storage slots (9–35), preserving armor and offhand slots.
20. F24: Shop Framework
[CRITICAL] Shop GUI Item Duplication & Barrier Block Drop Exploit
Affected Files/Classes:


ShopGui.java:28-67
Why it is a Problem: In onSlotClick(), PICKUP is handled and QUICK_MOVE/SWAP are intercepted, but any other action—specifically THROW (drop key 'Q') and CLONE (creative middle click)—falls through to vanilla's super.onSlotClick(). Hovering over any shop item and pressing 'Q' drops that item into the world for free without deducting currency. Hovering over slot 53 and pressing 'Q' drops illegal Items.BARRIER blocks into the world.
Recommended Fix: Cancel and consume all non-PICKUP actions on shop slots (0 <= slotIndex < 54):
java
if (slotIndex >= 0 && slotIndex < 54) {
    if (actionType != net.minecraft.screen.slot.SlotActionType.PICKUP) {
        this.sendContentUpdates();
        return;
    }
    // handle valid clicks...
}
[LOW] Missing Screen Updates on Successful Purchase
Affected Files/Classes:


ShopGui.java:47
Why it is a Problem: Line 47 returns immediately on successful purchase without calling this.sendContentUpdates(), causing client cursor and currency desynchronization.
Recommended Fix: Call this.sendContentUpdates() before returning.