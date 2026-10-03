# Miniverse — Gamemode Status

> **How to use this file**
> One row (or section) per gamemode. Update when framework adoption changes,
> a bug is found, or a migration step completes. This file is the first thing
> to paste into an AI session when working on a specific gamemode.
>
> Last updated: **2026-06-20**

---

## Quick Reference Matrix

**Legend:** ✅ Fully Used · ⚠️ Partially Used · ❌ Not Used · 🔄 Migration In Progress

| Framework | Manhunt | Speedrun | BountyHunt | DeathSwap | ResourceSprint | BlockShuffle | DeathShuffle | Duels | MurderMystery | Bridge | Infection | PillarsOfFortune | HordeSurvival | Dropper | Zombies | MicroParty |
|-----------|:-------:|:--------:|:----------:|:---------:|:--------------:|:------------:|:------------:|:-----:|:-------------:|:------:|:---------:|:----------------:|:-------------:|:-------:|:-------:|:-----------:|
| F01 Session | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F02 Match Lifecycle | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F03 Freeze | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F04 Spectator | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F05 Death Lifecycle | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F06 Persistence | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F07 Global Rules | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F08 Team | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ |
| F09 Map Protection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F10 Region Trigger | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| F11 Map Editor | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| F12 Scoreboard | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F13 Protected Items | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| F14 Kit | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F15 Role | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F16 Visibility | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F17 Corpse | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F18 Arena | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F19 Countdown Svc | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ✅ |
| F20 Player Snapshot | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F21 Derangement | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F22 Respawn Policy | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F23 Inventory Layout | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F24 Shop Framework | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | 🔄 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **Compliance %** | **76%** | **62%** | **74%** | **68%** | **62%** | **63%** | **64%** | **71%** | **76%** | **79%** | **66%** | **N/A** | **75%** | **78%** | **75%** | **77%** |

---

### 🌿 Universal Map Rule: Leaf Decay Prevention & Map Protection (F09)

> **CRITICAL ARCHITECTURE INVARIANT**: In all map-based environments, **leaf decay is 100% disabled**.
>
> - **Where Leaf Decay is OFF**:
>   1. **Map Editor sessions** (`SessionMode.MAP_EDITOR`).
>   2. **Inspection sessions** (`SessionMode.INSPECTION_SESSION`).
>   3. **All Map-based Gamemodes**: Murder Mystery, Bedwars, Duels, Infection, The Bridge, Pillars of Fortune, Zombies, Dropper, Micro Party, Skywars, Capture the Flag (identified via `MapWorldRules.isLeafDecayDisabled(world)`).
> - **How it works**:
>   - `LeavesBlockMixin` intercepts `LeavesBlock.randomTick` at `HEAD` and cancels decay whenever `MapWorldRules.isLeafDecayDisabled(world)` is true.
>   - Leaves placed during map construction (oak, birch, spruce, jungle, acacia, dark oak, mangrove, cherry, azalea) will **never** decay, despawn, or drop saplings/apples.
> - **Survival Minigames Untouched**:
>   - Natural vanilla survival minigames (Speedrun, Manhunt, Block Shuffle, Death Swap, Bounty Hunt, Death Shuffle, Resource Sprint, Horde Survival) retain standard vanilla leaf decay.
> - **Map Editor World Rules**:
>   - Map Editor automatically enforces daytime (`time set 6000`), clear weather (`weather clear`), `doDaylightCycle=false`, `doWeatherCycle=false`, `doMobSpawning=false`, `doFireTick=false`, `doVinesSpread=false`, `doInsomnia=false`, `doPatrolSpawning=false`, `doTraderSpawning=false`, `spawn-animals: false`, `spawn-npcs: false`.
> - **Developer note for the future**:
>   - If you ever decide to allow leaf decay on a specific map or gamemode in the future, adjust `MapWorldRules.isLeafDecayDisabled(world)` in `dev.frost.miniverse.map.MapWorldRules`.


---

## Per-Gamemode Detail

---

### Manhunt

**Main class:** `ManhuntMinigame`
**Status:** Production-near · **Compliance:** 76%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle ✅
- F08 Team (TeamManager + VanillaTeamAdapter)
- F09 Map Protection, F12 Scoreboard (sidebar timer)
- F13 Protected Items (tracker compass)
- F20 Player Snapshot (full persistence via `saveRuntimeState` override)
- `DynamicParticipantMinigame`, `PauseAwareMinigame`, `RosterAware`

**Frameworks NOT used:**
- F19 Countdown Service — Has its own timer implementation

**Known issues / debt:**
- None.

**Migration target:** None currently.

**Notes for AI sessions working on Manhunt:**
> F05 Death Lifecycle migration is complete. The custom `ManhuntSpeedrunnerRespawnSystem` has been successfully replaced by the unified framework.

---

### Speedrun

**Main class:** `SpeedrunMinigame`
**Status:** Production-near · **Compliance:** 62%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=true`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle (callbacks only, interceptsRespawn=false)
- F09 Map Protection, F12 Scoreboard
- `DynamicParticipantMinigame`, `PauseAwareMinigame`, `PlayerRespawnAware`,
  `PlayerLeaveAware`, `EntityDeathAware`, VanillaTeamAdapter

**Frameworks NOT used:**
- F08 Team — single-player runner, no teams

**Known issues / debt:**
- None.

**Migration target:** None currently

---

### BountyHunt

**Main class:** `BountyHuntMinigame`
**Status:** Production-near · **Compliance:** 79%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle (Full elimination/respawn flow)
- F08 Team (TeamManager + TeamManagerProvider), F09 Map Protection, F12 Scoreboard
- F13 Protected Items (tracker compass), F20 Player Snapshot (persistence)
- `DynamicParticipantMinigame`, `RosterAware`, `PauseAwareMinigame`,
  `PlayerLeaveAware`, `PlayerDamageAware`, VanillaTeamAdapter
- `ProtectionOverlaySender` (grace period rendering)

**Frameworks NOT used:**

**Known issues / debt:**
- `announcedGraceThresholds` set — manual reimplementation of F19 CountdownService.

**Migration target:** F19 CountdownService (Phase C)

---

### DeathSwap

**Main class:** `DeathSwapMinigame`
**Status:** Production-near · **Compliance:** 68%
**Last reviewed:** 2026-06-20

**Gamerules:** Full constructor: `keepInventory=true, doImmediateRespawn=true, pvp=true, daylight=true, weather=true, fallDamage=true, naturalRegen=true, advancements=false`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle (Full points/respawn flow)
- F08 Team, F09 Map Protection, F12 Scoreboard
- F19 Countdown Service (**correct usage**)
- F20 Player Snapshot, F21 Derangement/Swap

**Frameworks NOT used:**

- None.

**Migration target:** None currently

---

### ResourceSprint

**Main class:** `ResourceSprintMinigame`
**Status:** Production · **Compliance:** 62%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle ✅
- F06 Persistence ✅
- F08 Team (TeamManager + TeamManagerProvider), F09 Map Protection
- F12 Scoreboard, `PauseAwareMinigame`, `PlayerLeaveAware`, `PlayerRespawnAware`, VanillaTeamAdapter

**Frameworks NOT used:**
- F19 Countdown Service — has `timeWarningsShown` set (manual reimplementation)

**Known issues / debt:**
- `timeWarningsShown` Set — replace with F19 CountdownService.
- F05 not strictly required (no PvP death handling), but if damage is ever added, adopt then.

**Migration target:** F19 CountdownService (Phase C)

---

### BlockShuffle

**Main class:** `BlockShuffleMinigame`
**Status:** Needs work · **Compliance:** 63%
**Last reviewed:** 2026-06-20

**Gamerules:** Full constructor: `keepInventory=true, doImmediateRespawn=true, pvp=true, daylight=true, weather=true, fallDamage=true, naturalRegen=true, advancements=false`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator (directly, for elimination)
- F06 Persistence ✅
- F08 Team (TeamManager + VanillaTeamAdapter), F09 Map Protection, F12 Scoreboard
- `DynamicParticipantMinigame`, `PauseAwareMinigame`

**Frameworks NOT used:**
- F05 Death Lifecycle
- F19 Countdown Service — `timeWarningsShown` set (manual reimplementation)

**Confirmed bugs (active):**
- **B03:** Calls `SpectatorService.getInstance().clearAll()` in `initialize()` — double-clear
- **B04:** No `onPlayerLeave` — disconnected players stay in `activePlayers` indefinitely

**Known issues / debt:**
- `timeWarningsShown` set — replace with F19.
- Shares >80% of round logic with DeathShuffle. Strong candidate for shared
  Objective Round Framework extraction.

**Migration target:** Fix B03, B04 first. Then F19 CountdownService. Candidate for rewrite.

**Notes for AI sessions working on BlockShuffle:**
> Fix B03 and B04 before adding any other features. The `onPlayerLeave` gap blocks
> win-condition evaluation for the entire match when a player disconnects.

---

### DeathShuffle

**Main class:** `DeathShuffleMinigame`
**Status:** Needs work · **Compliance:** 64%
**Last reviewed:** 2026-06-20

**Gamerules:** Full constructor: `keepInventory=true, doImmediateRespawn=true, pvp=true, daylight=true, weather=true, fallDamage=true, naturalRegen=true, advancements=false`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle ✅
- F08 Team (TeamManager + VanillaTeamAdapter), F09 Map Protection, F12 Scoreboard
- `PersistentMinigame` (partial), `DynamicParticipantMinigame`, `PauseAwareMinigame`,
  `PlayerLeaveAware`

**Frameworks NOT used:**
- F19 Countdown Service — `timeWarningsShown` set (manual reimplementation)

**Known issues / debt:**
- **B04 variant:** `PlayerLeaveAware` present but unclear if `activePlayers` is cleaned up.
  Verify disconnect path removes player from all round tracking sets.
- `DeathObjectiveRegistry` is a bespoke parallel to a general Objective framework —
  not connected to any shared abstraction.
- Shares >80% of round logic with BlockShuffle.

**Migration target:** F19 CountdownService. Candidate for rewrite alongside BlockShuffle.

---

### Duels

**Main class:** `DuelsMinigame`
**Status:** Production-near · **Compliance:** 71%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=true`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- **F05 Death Lifecycle** ✅ (reference implementation)
- F08 Team (TeamManager + VanillaTeamAdapter), F09 Map Protection
- F14 Kit, F18 Arena, `SpawnPointAware`, Map Editor
- Custom: `DuelsDeathPolicy`, `DuelsSpectatorPolicy`, `DuelsRespawnStrategy`, `DuelsDeathCallbacks`

**Frameworks NOT used:**
- F12 Scoreboard — no sidebar
- F06 Persistence — no state saved

**Known issues / debt:**
- No scoreboard sidebar.
- `applyGamerules()` / `restoreGamerules()` are per-instance Duels-specific overrides
  for `naturalRegen` — sits alongside GlobalMatchRules in a slightly awkward way.

**Notes for AI sessions working on Duels:**
> Duels is the canonical F05 reference implementation. If you need to see
> how Death Lifecycle is wired correctly, read `DuelsMinigame` + `DuelsDeathCallbacks`.

---

### MurderMystery

**Main class:** `MurderMysteryMinigame`
**Status:** Production-near · **Compliance:** 76%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=true`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- **F05 Death Lifecycle** ✅ (second reference implementation)
- F06 Persistence ✅
- F09 Map Protection, F12 Scoreboard
- F15 Role (Murderer/Detective/Innocent/Spectator roles)
- F16 Visibility (role-based name-tag rules)
- F17 Corpse (armor stand at death location)
- `SpawnPointAware`, `PauseAwareMinigame`, `DynamicParticipantMinigame`, Map Editor
- Custom death: `MurderMysteryDeathPolicy`, `MurderMysterySpectatorPolicy`,
  `MurderMysteryRespawnStrategy`, `MurderMysteryDeathCallbacks`
- Bespoke subsystems: `VirtualEconomyManager`, `CoinManager`, `ShopManager`,
  `MurderMysteryWeaponManager`, `MurderMysteryWinConditionManager`

**Frameworks NOT used:**
- F08 Team — all-vs-all (roles don't map to symmetric teams)

**Known issues / debt:**
- `GameState` enum is now correctly consolidated to canonical values (see DECISIONS.md D03).
- `deathLifecycleManager` is initialised in `onMatchStart()`, so pre-game deaths
  (between `initialize()` and `onMatchStart()`) fall through to the `AbstractMinigame`
  no-op `onPlayerDeath()`. This is acceptable but should be documented in the source.
- Virtual economy is a bespoke subsystem with no shared framework equivalent yet.

**Notes for AI sessions working on MurderMystery:**
> MurderMystery is the second canonical F05 reference implementation.
> The `allowDamage` method is the primary death entry point — it calls
> `deathLifecycleManager.handleFatalDamage` directly rather than going through
> `onEntityDeath`. This is intentional: MurderMystery intercepts all damage at the
> `allowDamage` level to apply role-based rules, then delegates to the framework.
> Do not add a second death entry point.

---

### Bridge

**Main class:** `BridgeMinigame`
**Status:** Production-near · **Compliance:** 75%
**Last reviewed:** 2026-06-20

**Gamerules:** Full constructor: `keepInventory=true, doImmediateRespawn=true, pvp=true, daylight=true, weather=true, fallDamage=true, naturalRegen=true, advancements=false`

**Note:** F05 Death Lifecycle adopted successfully.

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle ✅
- F08 Team (TeamManager + TeamManagerProvider), F09 Map Protection
- F10 Region Trigger (**only production user** — correct pattern for goal detection)
- F12 Scoreboard, F20 Player Snapshot, F23 Inventory Layout
- `SpawnPointAware`, `PauseAwareMinigame`, `PlayerDamageAware`, `PlayerRegionAware`, Map Editor
- Full `PersistentMinigame` override (scores, game state)

**Frameworks NOT used:**
- F14 Kit — gives items manually
- F19 Countdown Service — timer logic reimplemented inline

**Known issues / debt:**
- Has its own field-reset logic (no F18 Arena). Acceptable given it has one field.

**Migration target:** F19 CountdownService (Phase C)

---

### Infection

**Main class:** `InfectionMinigame`
**Status:** Production-near · **Compliance:** 66%
**Last reviewed:** 2026-06-20

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F08 Team (TeamManager + TeamManagerProvider), F09 Map Protection
- F12 Scoreboard, F20 Player Snapshot (full persistence)
- `SpawnPointAware`, `PauseAwareMinigame`, `PlayerRespawnAware`, `PlayerDamageAware`, VanillaTeamAdapter

**Frameworks NOT used:**
- F05 Death Lifecycle — death-conversion mechanic (death → join infected team) handled inline
- F15 Role — survivor/infected tracked via team membership sets instead of `RoleManager`
- F16 Visibility — name-tag rules applied via vanilla team settings, not `VisibilityManager`

**Known issues / debt:**
- Survivor/infected role tracking via raw sets instead of F15 `RoleManager`. Migrating
  to F15 would enable F16 `VisibilityManager` and clean up ad-hoc `survivors`/`infected` set checks.
- Death-conversion mechanic is an interesting F05 use case: `DeathPolicy.execute()` would
  run conversion logic; `PostDeathPolicy` would be immediate respawn in infected mode.

**Migration target:** F05 Death Lifecycle (Phase B), F15 Role + F16 Visibility (Phase C)

---

## Adding a New Gamemode

When adding a new gamemode, copy the template below and fill it in before writing
any code. Add the gamemode column to the matrix above.

### Pillars of Fortune

**Main class:** `PillarsOfFortuneMinigame`
**Status:** Prototype
**Compliance:** N/A
**Last reviewed:** 2026-07-09

**Gamerules:** `doImmediateRespawn=false`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle (Death handling and spectate forever policy)
- F06 Persistence / F20 Player Snapshot (Crash recovery for timers and game state)
- F09 Map Protection (Protects map blocks from destruction natively)
- F11 Map Editor (SPAWN_POINT markers)
- F12 Scoreboard (Displays remaining players and loot timer)
- F19 Countdown Service (Used by LootDropModule for announcing loot drops)

**Frameworks NOT used:**
- F08 Team — (Free for all)

**Known issues / debt:**
- None.

**Migration target:** None currently

**Notes for AI sessions working on Pillars of Fortune:**
> Map pillars are not dynamically generated; they must be built in the map with SPAWN_POINT markers placed on them.

---

### Horde Survival

**Main class:** `HordeSurvivalMinigame`
**Status:** Production-ready · **Compliance:** 75%
**Last reviewed:** 2026-09-11

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=true`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle (Single-life with spectator transition, match loss on squad wipe)
- F06 Persistence / F20 Player Snapshot (Full round state, wave number, coins, campfire fuel recovery)
- F08 Team (TeamManager + VanillaTeamAdapter with unified "Survivors" team, friendly fire disabled)
- F09 Map Protection (Barricade break control, dynamic world border shrinking / expansion)
- F12 Scoreboard (Sidebar display of Current Wave, Remaining Mobs, Extraction Timer, Player Coin Balance)
- F24 Shop Framework (Integrated ShopGui with VirtualCoinCurrency and HordeShopProvider)

**Key Mechanics & Modules:**
- **Zero-Map Requirement**: Operates in any natural world; dynamically computes surface spawns and relocation sites.
- **Dynamic Wave Engine**: Batch mob spawning 25-45m from survivors, mob scaling per round, and custom anti-camping siege mobs (Miner Zombie, Harpoon Drowned, Sapper Creeper).
- **Nomad Beacon & Hallowed Campfire**: Wandering merchant with vertical sky beam relocating every 2 waves; safe campfire sanctuary fueled by sacrificing mob drops.
- **Intermission Supply Meteors**: Supply drops landing during intermissions with bonus ammo, defense blocks, and supplies.
- **Climax Extraction Run**: Final wave objective (default Wave 15) triggering an LZ marker 350m away with a 3-minute sprint and 10-second hold-the-zone helicopter extraction.
- **DeathSwap-Style Workspace View**: Full client GUI with team assignment ("Available" to "Survivors"), configurable round counts (15, 20, 30), intermission durations, and campfire fuels.

**Known issues / debt:**
- None.

**Migration target:** None currently.

---

### Dropper

**Main class:** `DropperMinigame`
**Status:** Production-ready · **Compliance:** 78%
**Last reviewed:** 2026-09-18

**Gamerules:** `keepInventory=true`, `doImmediateRespawn=true`, `fallDamage=false` (handled by fail logic)

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze (5s countdown freeze)
- F04 Spectator (`SpectatorPolicies.unrestricted()`, `SpectatorTargetProviders.roster()` with teleport hotbar upon completion)
- F05 Death Lifecycle (Damage cancellation on lethal fall/void damage, instant snap back without death screen)
- F06 Persistence / F20 Player Snapshot (Level index, fail counters, completion times)
- F09 Map Protection (Full block break/place denial)
- F10 Region Trigger (Goal detection via `PlayerRegionAware` and `level_goal`)
- F11 Map Editor (`level_config` parent marker with child `level_spawn` and `level_goal`, `lobby_spawn`)
- F12 Scoreboard (Dynamic sidebar displaying Level progress, Elapsed Time, Fails, and Final Countdown)
- F19 Countdown Svc (Post-first-finish 60s countdown)

**Key Mechanics & Modules:**
- **Single-World Multi-Level Architecture**: Multiple dropper maps pasted into a single world, detected automatically via `dropper.json` or `dropper_level` tags.
- **Client Workspace Inspection**: Interactive map selection that reveals detected levels, with toggles to include/exclude levels from the active pool.
- **Customizable Level Selection**: Supports `ALL_SEQUENTIAL`, `ALL_SHUFFLED`, or `RANDOM_N` levels per match.
- **Instant Respawn & Fail Tracking**: Lethal damage is caught and cancelled before death screen triggers; player is reset to the level spawn with sound effects and fail counter incremented.
- **Configurable Final Countdown**: First player to complete all levels starts a 60s countdown for remaining runners.
- **Stuck Skip Command**: Players failing >= 20 times (configurable and toggleable in Match Rules) can use `/dropper skip` to skip to the next level.
- **Spectator Transition with Player Teleport**: Finishing all levels switches player to spectator mode with hotbar teleportation enabled.

**Known issues / debt:**
- None.

**Migration target:** None currently.

---

### Zombies

**Main class:** `ZombiesMinigame`
**Status:** Production-ready · **Compliance:** 75%
**Last reviewed:** 2026-09-18

**Gamerules:** `keepInventory=true`, `doImmediateRespawn=true`, `doMobSpawning=false` (manual spawning via `ZombieEntityManager` completely bypasses this rule)

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze (`DownedPlayerTracker`), F04 Spectator
- F05 Death Lifecycle (Downed crawl state, teammate revives, bleedout to spectator)
- F06 Persistence / F20 Player Snapshot (Rounds, gold balance, active perks, weapons)
- F08 Team (`TeamManagerProvider` with unified "Survivors" team)
- F09 Map Protection (`BlockProtectionProvider`, map break denial, **Leaf decay completely disabled**)
- F11 Map Editor (Extensive marker suite: windows, doors, perks, weapon shops, lucky chests, power switch, team/ultimate machines, zombie spawns)
- F12 Scoreboard (Dynamic sidebar tracking rounds, remaining zombies, gold, perks, power status)
- F13 Protected Items (`ProtectedItemService`, gun hotbar management, right-click prevention for perk items)

**Key Mechanics & Modules:**
- **Wave Engine**: Configurable round-based spawning with scaling zombie counts, boss rounds, and intermission countdowns.
- **Entity Management**: `ZombieEntityManager` spawns zombies with custom speeds, health, armor, and target-finding, completely independent of vanilla `doMobSpawning`.
- **Guns & Hotbar**: Left-click / right-click shooting, reloading, ammo tracking, weapon purchases, and Pack-a-Punch / Ultimate weapon upgrades.
- **Interactive Map Elements**: Window barricades (repairable with gold reward), purchasable barrier doors, power switches, lucky chests, armor shops.
- **Downed & Revive System**: Crawling pose when downed, revive progress timer with teammates, spectator mode on full bleedout.

**Known issues / debt:**
- None.

**Migration target:** None currently.

---

### Micro Party (Micro-Party)

**Main class:** `MicroPartyMinigame`
**Status:** Production-ready · **Compliance:** 77%
**Last reviewed:** 2026-09-26

**Gamerules:** `doImmediateRespawn=false`, `keepInventory=true`, `fallDamage=false`, `doMobSpawning=false`, `doDaylightCycle=false`

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze (intermission breathers and start countdown)
- F04 Spectator (`SpectatorPolicies.unrestricted()`, `SpectatorTargetProviders.roster()`)
- F05 Death Lifecycle (Damage cancellation on void/hazards, life pool management, spectator transition on elimination)
- F06 Persistence / F20 Player Snapshot (Round count, active rule, completed states)
- F09 Map Protection (Full arena protection from breaking, temporary entity cleanup, leaf decay disabled)
- F10 Region Trigger (Bounds exit fail detection via `ARENA_BOUNDS`, `COLOR_ZONE`, and `HIGH_GROUND` triggers)
- F11 Map Editor (`arena_bounds`, `arena_center`, `player_spawns`, `lobby_spawns`, `color_zones`, `high_ground`, `targets`)
- F12 Scoreboard (Dynamic sidebar displaying Round, Speed multiplier, Task prompt, Timer, and Player Lives `♥♥♥`)
- F19 Countdown Svc (Micro-timer visible announcements and SFX cues)

**Key Mechanics & Modules:**
- **MicroRule Engine**: Extensible rule deck cycling through rapid 3–8s micro-challenges (Statue, Rapid Crouch, Jump Mania, Look Up, Look Down, 360 Spin, Slap a Friend, Drop Item, Reverse Psychology, Center Stage, Color Rush, Floor is Lava, Anvil Dodge).
- **Speed-Up Escalation**: Every 5 rounds, tempo accelerates (+pitch, faster countdowns, Speed I/II effects).
- **Survival & Points Modes**: Configurable lives (default 3) elimination or fixed round point rush.
- **Dynamic Fallback**: Operates on any map with simple bounds and spawns, activating color/high-ground rules dynamically if markers exist.

**Known issues / debt:**
- None.

**Migration target:** None currently.

---

### Capture the Flag (CTF)

**Main class:** `CaptureTheFlagMinigame`
**Status:** Production-ready · **Compliance:** 82%
**Last reviewed:** 2026-10-02

**Gamerules:** `keepInventory=false`, `doImmediateRespawn=false` (timed respawn delay)

**Frameworks actively used:**
- F01 Session, F02 Match Lifecycle, F03 Freeze, F04 Spectator
- F05 Death Lifecycle ✅ (`CtfDeathLifecycleConfig`, `CtfRespawnStrategy`, `CtfConditionalSpectatorPolicy`)
- F06 Persistence (`PersistentMinigame`)
- F08 Team (`TeamManager` + `VanillaTeamAdapter`, dynamic 2–8 teams supported)
- F09 Map Protection (`ArenaTracker`, leaf decay disabled, protected base structures)
- F11 Map Editor (`MapEditorExtension` with teamConfigs, teamSpawns, teamFlags, teamDropoffs, shopNpcs, powerupLocations, voidLevel)
- F12 Scoreboard (`ScoreboardTemplate` dynamic live flag HUD, status, coins/gems)
- F20 Player Snapshot (full session roster)
- F23 Inventory Layout (`InventoryLayoutAware`)
- F24 Shop Framework (`CtfShopManager` with multi-category `ShopGui`, permanent armor/weapon/enchantment upgrades, consumable blocks/combat/utility)

**Key Mechanics & Modules:**
- **Dynamic 2–8 Teams**: Flexible team scaling configured per-map through the Map Editor.
- **Dual Mode System**: Toggleable between **Elimination Mode** (capturing a team's flag permanently destroys their respawns; eliminate remaining players) and **Standard Mode** (race to target captures).
- **Banner Flag Carrier**: Real team banner worn on carrier's helmet slot (`EquipmentSlot.HEAD`), glowing aura effect, dropped banner with ticking hologram return timer (`[15s]`), instant friendly recovery, and base return tension ("friendly flag must be at base to capture").
- **CTF Economy & Shop**: Double currency (Coins & Gems) earned from kills (+bounties +33% wallet theft), flag grabs (+20c/+8g), and captures (+80c/+30g). Interactive NPC shop providing tiered armor/weapons, utility, bridge eggs, and consumable buffs.
- **Powerup Stations**: Periodic spawning of Speed, Strength, Absorption, Instant Heal, Jump Boost, Coins, Gems, and Bridge Eggs at designated map markers.
- **Client Workspace**: Integrated `CaptureTheFlagWorkspaceView` featuring `DynamicTeamSelectionGrid`, map selector, and customizable game rule toggles.

**Known issues / debt:**
- None.

**Migration target:** None currently.

---

## Adding a New Gamemode

When adding a new gamemode, copy the template below and fill it in before writing
any code. Add the gamemode column to the matrix above.

```markdown
### [Gamemode Name]

**Main class:** `[Name]Minigame`
**Status:** Draft / In Development / Production-near / Production-ready
**Compliance:** XX%
**Last reviewed:** YYYY-MM-DD

**Gamerules:** `rule=value`

**Frameworks actively used:**
- ...

**Frameworks NOT used:**
- ...

**Known issues / debt:**
- ...

**Migration target:** ...
```
