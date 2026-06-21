# Miniverse Agent Guide

This project is a Fabric Minecraft minigame framework. It runs a main server with session management UI, then launches child backend servers for individual minigame sessions.

**Minecraft version:** 1.21.1

> **CRITICAL RULE FOR ALL AI SESSIONS:**
> Every time you make an important architectural change, refactor, or framework integration, you **MUST** document it in the relevant Markdown files under `docs/status/` (e.g., `DECISIONS.md`, `MIGRATION_PLAN.md`, `ARCHITECTURE.md`, `MINIVERSE_FRAMEWORK_AUDIT.md`, `GAMEMODE_STATUS.md`). Do not wait for explicit instruction to do this.

## The Source of Truth

The historical information that used to be in this file has been superseded. The definitive, actively maintained documentation for this project lives exclusively in the `docs/status/` directory.

Before making any changes to the framework or gamemodes, you **MUST** consult these files:

- **`docs/status/ARCHITECTURE.md`**
  Contains the latest framework-level architecture overview, package responsibilities, and system design.
  
- **`docs/status/DECISIONS.md`**
  The source of truth for all architectural decisions. If you need to know *why* something is designed a certain way, check here. Do not contradict a `DECIDED` entry without explicit instruction.

- **`docs/status/MIGRATION_PLAN.md`**
  The ordered work sequence for migrating the codebase to a clean, framework-consistent state. Work strictly top-to-bottom through these phases.

- **`docs/status/MINIVERSE_FRAMEWORK_AUDIT.md`**
  Outlines known architectural debts, framework bugs, and inconsistencies that are queued to be addressed.

- **`docs/status/GAMEMODE_STATUS.md`**
  Tracks the current health, framework adoption, and outstanding bugs for every gamemode.

**Remember:** Do not rely on outdated assumptions. Always defer to the `docs/status/` files, and always update them when you complete significant work.
