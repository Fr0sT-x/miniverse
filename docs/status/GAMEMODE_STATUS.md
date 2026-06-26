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

| Framework | Manhunt | Speedrun | BountyHunt | DeathSwap | ResourceSprint | BlockShuffle | DeathShuffle | Duels | MurderMystery | Bridge | Infection |
|-----------|:-------:|:--------:|:----------:|:---------:|:--------------:|:------------:|:------------:|:-----:|:-------------:|:------:|:---------:|
| F01 Session | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F02 Match Lifecycle | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F03 Freeze | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F04 Spectator | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F05 Death Lifecycle | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F06 Persistence | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F07 Global Rules | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F08 Team | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| F09 Map Protection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F10 Region Trigger | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| F11 Map Editor | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| F12 Scoreboard | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| F13 Protected Items | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F14 Kit | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| F15 Role | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| F16 Visibility | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| F17 Corpse | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| F18 Arena | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| F19 Countdown Svc | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| F20 Player Snapshot | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F21 Derangement | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F22 Respawn Policy | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| F23 Inventory Layout | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Compliance %** | **76%** | **62%** | **74%** | **68%** | **62%** | **63%** | **64%** | **71%** | **76%** | **79%** | **66%** |

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

```markdown
### GamemodeName

**Main class:** `GamemodeNameMinigame`
**Status:** [Prototype | In Progress | Production-near | Production]
**Compliance:** X%
**Last reviewed:** YYYY-MM-DD

**Gamerules:** `keepInventory=X`, `doImmediateRespawn=X`, other overrides if any

**Frameworks actively used:**
- List frameworks with brief reason

**Frameworks NOT used:**
- List frameworks with brief reason why not

**Known issues / debt:**
- List known issues

**Migration target:** What frameworks to adopt and when

**Notes for AI sessions working on GamemodeName:**
> Key context needed for consistent AI-assisted work on this gamemode.
```
