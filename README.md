# Planned Features (Feature Backlog)

This document provides an overview of the planned administration and moderation features.

## 📋 Overview

| Feature | Category              | Description | Priority | Status |
| :--- |:----------------------| :--- | :--- | :--- |
| **Chat Timeouts** | Moderation            | Temporarily restricts players from sending chat messages. | High | ✅ Implemented |
| **Vanish** | Administration        | Makes staff members invisible to regular players. | High | ✅ Implemented |
| **Disguise** | Administration /disguise | Allows players/staff to disguise as another mob or player. | Medium | ⏳ Planned |
| **Freeze** | Moderation            | Immobilizes a player in place (e.g., during suspected hacking). | High | ⏳ Planned |

## 🛠️ Detailed Feature Breakdown

### 1. Chat Timeouts ✅

* **Goal:** Temporarily mute players who violate chat rules.
* **Commands:** `/timeout <player> <duration> [reason]` (alias: `/mute`), `/timeout info <player>`, `/unmute <player>`, `/mutelist [page]`
* **Features:**
  * Duration formats in seconds, minutes, hours or days, combinable (e.g., `30s`, `10m`, `1h`, `1d12h`). Without a duration the timeout is permanent.
  * Automatic message sent to the target player showing reason and duration.
  * Blocks normal chat as well as `/msg`, `/tell`, `/w`, `/me`, `/say` and `/teammsg`. All other commands keep working.
  * Timeouts survive restarts (`config/maintena/mutes.json`); texts are configurable in `mute-config.json`.
  * Expired timeouts are removed automatically and the player is notified.
  * Also available in the settings menu (`/maintena mute`).

### 2. Vanish ✅

* **Goal:** Observe players unnoticed for moderation purposes.
* **Commands:** `/vanish`, `/v` (toggle), `/vanish on|off|status|list`
* **Features:**
  * Hides the staff member from the tab list and the in-game world.
  * Operators (gamemaster permission) can still see vanished staff; regular players cannot.
  * Regular players get a "left the game" message when someone vanishes and a "joined the game" message when they become visible again.
  * No entity collisions while vanished: vanished staff neither push nor get pushed by mobs, entities, boats or minecarts. Item pickup works normally.
  * Actionbar hint while vanished; state is kept on re-join, but reset on server restart.
  * Also available in the settings menu (`/maintena vanish`).
* **Known limitations:** the real join/leave messages when a vanished player actually disconnects or reconnects, `/list`, the server-list player count and tab-completion of names still reveal a vanished player.

### 3. Disguise

* **Goal:** Transform into an entity, mob, or another player for testing, events, or moderation.
* **Suggested Commands:** `/disguise <entity/player>`, `/undisguise`
* **Features:**
  * Adjusts hitbox and animations to match the chosen entity.
  * Permission-based access to specific disguises.

### 4. Freeze

* **Goal:** Lock players in place who are suspected of using unauthorized mods or cheats.
* **Suggested Commands:** `/freeze <player>`, `/unfreeze <player>`
* **Features:**
  * Prevents movement, interactions, and optionally chat input.
  * Displays instructions to the frozen player (e.g., to join Discord support).