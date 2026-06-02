# Miniverse Deep Architectural Review

This document explains how Miniverse currently behaves as a multiplayer session-runtime framework, then evaluates architectural risks and recommends a staged refactor direction.

## 1) High-Level Architectural Overview

Miniverse is effectively a three-plane architecture:

- **Control plane (main server):** session creation, assignment, launch/relaunch, backend process supervision, retention metadata, transfer orchestration.
  - Core ownership in `src/main/java/dev/frost/miniverse/session/SessionManager.java`.
- **Runtime plane (backend servers):** per-session/per-group minigame runtime, lifecycle phases, freeze/countdown, disconnect grace, runtime tick logic.
  - Core ownership in `src/main/java/dev/frost/miniverse/minigame/core/SessionBootstrapper.java` and `src/main/java/dev/frost/miniverse/minigame/core/lifecycle/MatchLifecycleController.java`.
- **Persistence plane (filesystem):**
  - session registry/index (`session.json`) managed by `SessionRegistry`.
  - backend runtime save (`miniverse-game-session.json`) managed by `MinigameSessionStore`.
  - backend launch/runtime config (`miniverse-session.json`) emitted by launcher and loaded by backend bootstrap.
  - vanilla world/playerdata retained inside backend directories.

Current startup wiring:

- `Miniverse` registers games/network/events and invokes session recovery on server started.
- `SessionManager` drives backend launches through `ServerLauncher`.
- Backend reads runtime config and enters `SessionBootstrapper` state machine.
- `SessionBootstrapper` creates minigame runtime, applies settings, optionally loads save state, gates startup by expected players and client-ready events, then starts lifecycle through `MatchLifecycleController`.

## 2) Current Strengths

- **Pragmatic retained relaunch support:** fresh launch and retained relaunch are both first-class in `SessionManager` + `ServerLauncher`.
- **Reasonable runtime save resiliency:** `MinigameSessionStore` keeps temp/backup/corrupt handling patterns and attempts restoration on bootstrap.
- **Client readiness handshake exists:** startup waits for players plus client-ready signaling, reducing early-start desync.
- **Lifecycle controller centralization:** freeze/start/run/end-return flow is not fully scattered; `MatchLifecycleController` provides one consistent lifecycle state machine.
- **Gamemode plug-in architecture exists:** game definitions + bootstrap handlers give a workable framework for multiple gamemodes.
- **Cross-server transfer integration is explicit:** transition/velocity transfer orchestration is integrated rather than ad-hoc command dispatch.

## 3) Current Weaknesses

- **State authority is split across too many mutable channels:**
  - in-memory `GameSession` state,
  - `session.json` lifecycle metadata/flags,
  - `miniverse-session.json` runtime properties,
  - `miniverse-game-session.json` runtime payload,
  - vanilla world/playerdata.
- **Registry is overloaded:** `SessionRegistry` acts as both durable index and command/control bus (stop/return/seed-change/pause flags), which couples persistence schema to orchestration behavior.
- **Cross-file updates are not transactional:** state transitions often update in-memory plus multiple files independently.
- **Global singleton concentration:** `SessionManager`, `MinigameManager`, `MatchLifecycleController`, static `SessionBootstrapper` state, and static routing state produce hidden coupling and difficult restart semantics.
- **Launch and lifecycle responsibilities are broad and mixed:** `SessionManager` combines orchestration, retention flow, transfer logic, and seed-change staging.
- **Restore semantics are not explicit contracts:** framework permits load, but exact preconditions/postconditions and invariant checks are not enforced consistently per gamemode.

## 4) Critical Bugs / Race-Condition Risks

- **Retained directory ambiguity risk:** retained backend selection relies on folder naming/modified-time heuristics, which can select wrong generation after repeated relaunch cycles.
- **Stale registry flag risk:** lingering `stopRequested` / `returnComplete` / seed-change flags can block or misdirect valid recovery and relaunch.
- **Static marker leakage in routing path:** seed-change announcement/completion marker maps in routing/event code can persist in-process and suppress future operations for same keys.
- **Port reservation TOCTOU risk:** launcher checks free port before process bind; races possible under concurrency.
- **Join vs restore ordering race:** player snapshots can be restored while runtime/startup gating is still transitioning, leading to mismatched mode/inventory/phase assumptions.
- **Lifecycle replay hazard:** restored sessions can still traverse start logic if state normalization and bootstrap gating misclassify restored state.
- **Pause/active/terminal boundary race:** bootstrap checks runtime state and saved-state flags in multiple places with non-atomic transitions.
- **Runtime reset ordering risk:** startup abort path sets stop flag and resets manager while transfer/return signaling may still be in progress.

## 5) Persistence / Lifecycle Analysis

### Authoritative vs duplicated vs transient state

- **Authoritative (should be):**
  - world + vanilla playerdata for vanilla state.
  - `miniverse-game-session.json` for non-vanilla runtime state continuity.
  - `session.json` for control-plane session index and retained metadata.
- **Duplicated (currently high):**
  - participants/team assignment/roles mirrored in runtime config, registry, and sometimes runtime payload.
  - lifecycle intent and progression reflected in both registry flags and runtime lifecycle state.
- **Transient:**
  - loading/ready maps, countdown internals, in-memory phase machine internals, executor queue state.
- **Reconstructed:**
  - parts of team/role/assignment state are rebuilt from properties + registry merge.
  - some runtime structures are recreated by gamemode hooks instead of persisted snapshots.

### Lifecycle and restore ordering dependencies

Observed ordering dependencies with user-visible failure impact:

- runtime instance must exist before save-load path can populate it.
- settings application currently precedes/overlaps save-load path and can reinitialize restored fields.
- player join admission and snapshot restoration can happen while startup gating is still waiting.
- lifecycle start freeze clears inventory/gamemode, which can conflict with restored player snapshots if ordering is wrong.
- countdown/freeze release and match-start packet emission must not trigger on already-active restored sessions.
- return/stop flags must only be set after all transfer outcomes are committed.

### Missing persistence or unsafe persistence patterns

- per-gamemode runtime fields are inconsistently captured.
- framework-level lifecycle internals (phase intent vs runtime state vs registry flags) are not represented as one canonical persisted state model.
- control-plane JSON writes are not consistently atomic across all critical files.
- no explicit generation token ties registry snapshot to specific retained backend generation.

## 6) Scalability Analysis

- **Launcher bottlenecks:** bounded thread pool + queue can reject launches under load; fallback path is user-facing failure rather than adaptive scheduling.
- **Single manager hotspot:** `SessionManager` becomes contention and change hotspot as session count/gamemode complexity grows.
- **Tick orchestration scaling risk:** gameplay tick path and routing tick path both perform cross-cutting checks; complexity rises non-linearly with more lifecycle features.
- **Filesystem scan/heuristic costs:** retained folder discovery and log polling are simple but can become expensive/noisy at scale.
- **State fan-out complexity:** same state written/read through multiple files increases consistency costs as session counts grow.
- **Operational observability gap:** architecture relies heavily on logs; lacks formal state-transition journaling and restore diagnostics for large multi-session fleets.

## 7) Framework Boundary Analysis

Current boundary quality:

- **Good:** bootstrap handler abstraction (`SessionBootstrapper.Handler`) gives a clear extension point.
- **Weak:** lifecycle and persistence contracts are implicit; gamemodes can rely on incidental ordering behavior.
- **Leakage from framework into gamemode:**
  - gamemodes rely on framework-managed properties/assignment conventions and lifecycle timing details.
- **Leakage from gamemode into framework:**
  - framework recovery quality depends on whether gamemode implements persistence and rehydration rigorously.
- **Result:** framework cannot guarantee restore safety uniformly; correctness depends on gamemode-specific discipline.

## 8) Restore-Flow Analysis (New Match vs Retained Relaunch)

### New-match bootstrap path

- main server creates/launches session/group(s).
- launcher creates fresh working directory, writes runtime config and server properties.
- backend bootstraps runtime, applies settings, waits expected players + client-ready + `canStart`.
- lifecycle enters start freeze, then running, then gamemode start callback.

### Restore-existing-session path

- recovery service/manual relaunch rebuilds session from retained metadata.
- launcher reuses retained backend directory (world + prior runtime save).
- backend bootstrap creates runtime, loads saved state from `miniverse-game-session.json`.
- reconnecting players may get immediate snapshot restore and bypass fresh-match startup sequence.

### Core mixing problem

Both flows converge in the same bootstrap/lifecycle machinery without a strict explicit mode boundary (fresh bootstrap vs restore-resume). This enables accidental replay of start semantics on restored sessions, or incorrect phase transitions when reconstructed state is incomplete/stale.

### Required architectural invariant (currently weak)

- A restored active session must not execute fresh-start side effects (inventory wipe, start freeze, start callback replay) unless explicitly transitioning from a valid waiting state.

## 9) Recommended Architectural Direction

Adopt a deterministic lifecycle orchestration model with explicit restore semantics:

- **Introduce explicit startup mode:** `NEW_MATCH`, `RESTORE_RESUME`, `INSPECTION`.
- **Define one authoritative runtime continuity model:** framework-level persisted envelope for lifecycle phase intent + timing + participant readiness + gamemode payload version.
- **Separate registry index from control commands:** keep `session.json` for index/metadata, move orchestration commands into generation-scoped control records (or clearly isolated command file).
- **Use generation tokens:** every launch/relaunch increments generation; all retained directory selection and control commands must match generation.
- **Enforce restore invariants centrally:** a restore coordinator validates persisted envelope and decides legal transitions before gameplay logic runs.
- **Keep abstractions practical:** no ECS rewrite, no service explosion, no external database dependency.

## 10) Recommended Staged Refactor Plan

### Stage 1 - Correctness Guards and Ordering Invariants

- add explicit startup mode and generation id propagation.
- hard-block fresh-start actions when restored state indicates active/paused/ending phases.
- centralize player snapshot restore ordering relative to freeze/start transitions.
- clear/reset all transient static markers on session completion/relaunch boundaries.
- enforce strict invariant checks with fail-fast diagnostics.

### Stage 2 - Persistence Contract Hardening

- introduce versioned runtime envelope schema (framework-owned) around gamemode payloads.
- make all control-plane/session JSON writes atomic (temp + fsync + replace) where feasible.
- split registry metadata from command flags; add idempotent command consumption semantics.
- attach generation id to retained directories and runtime saves; stop using modified-time heuristics as primary selector.

### Stage 3 - Framework/Gamemode Boundary Contracts

- formalize `PersistentMinigame` contract requirements:
  - mandatory owned-state declaration,
  - deterministic load hooks,
  - compatibility/version handling.
- add framework restore-compliance checks per gamemode.
- move gamemode-specific restore hacks out of framework routing/bootstrap paths.

### Stage 4 - Scalability and Operability

- introduce transition journal/events for lifecycle state changes.
- add backpressure-aware launch scheduling and richer operator feedback.
- improve observability for restore failures (reason codes, invariant violations, generation mismatches).
- reduce global singleton coupling where high-value (injectable coordinators/services).

## 11) Immediate High-Value Fixes

- add generation marker file in each backend working directory and select retained backend by generation first, not mtime.
- clear/expire seed-change and return/stop transient markers deterministically at lifecycle boundaries.
- prevent lifecycle start callback from executing during restore-resume unless persisted state explicitly requires it.
- gate inventory reset/start freeze behind `NEW_MATCH` mode only.
- ensure snapshot restore runs before any gamemode logic that assumes live inventory/position.
- convert critical `session.json` writes to atomic replace strategy.
- add a startup diagnostic dump that logs chosen restore source files, generation, phase, and rejected invariants.

## 12) Long-Term Framework Recommendations

- define Miniverse runtime-state taxonomy:
  - `authoritative-persisted`,
  - `derived-reconstructable`,
  - `transient-nonrecoverable`.
- require every gamemode to ship a restore-safety profile and persistence test suite.
- add crash/relaunch simulation tests as framework CI gates.
- encode lifecycle as explicit legal state-transition graph and validate transitions at runtime.
- maintain compatibility policy for save schema evolution with explicit migration handlers.

---

## Gamemode Restore-Safety Classification

- **Manhunt:** partially restore-safe.
  - Persists substantial runtime state; still exposed to reconnect/assignment timing edge cases.
- **Bounty Hunt:** partially restore-safe.
  - Persists major assignment/timer/score state; join-order and dangling-target issues remain.
- **Death Swap:** partially restore-safe.
  - Persists many runtime fields; some team/assignment reconstruction remains fragile on restore boundary.
- **Speedrun:** unsafe.
  - Lacks full game-owned runtime persistence; restored session can lose run continuity semantics.
- **Resource Sprint:** unsafe.
  - Lacks full game-owned runtime persistence; objective progress/timing state continuity is not guaranteed.

## Architectural Transition Summary

Miniverse already has most components required for persistent resumable session runtimes, but they are currently composed as layered heuristics rather than a strict restore contract. The shortest safe path is not a rewrite: it is a deterministic restore coordinator, generation-scoped control semantics, and explicit framework-gamemode persistence boundaries.
