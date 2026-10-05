# Planned Features (Feature Backlog)

This document provides an overview of the planned administration and moderation features.

## 📋 Overview

| Feature | Category              | Description | Priority |
| :--- |:----------------------| :--- | :--- |
| **Chat Timeouts** | Moderation            | Temporarily restricts players from sending chat messages. | High |
| **Vanish** | Administration        | Makes staff members invisible to regular players. | High |
| **Disguise** | Administration /disguise | Allows players/staff to disguise as another mob or player. | Medium |
| **Freeze** | Moderation            | Immobilizes a player in place (e.g., during suspected hacking). | High |

## 🛠️ Detailed Feature Breakdown

### 1. Chat Timeouts

* **Goal:** Temporarily mute players who violate chat rules.
* **Suggested Commands:** `/timeout <player> <duration> [reason]`
* **Features:**
  * Duration formats in seconds, minutes, or hours (e.g., `10m`, `1h`).
  * Automatic message sent to the target player showing reason and duration.

### 2. Vanish

* **Goal:** Observe players unnoticed for moderation purposes.
* **Suggested Commands:** `/vanish`, `/v`
* **Features:**
  * Hides the staff member from the tab list and the in-game world.
  * Disables item pickup while vanished to avoid interfering with gameplay.

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


