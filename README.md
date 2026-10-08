# Planned Features (Feature Backlog)

This document provides an overview of the planned administration and moderation features.

## 📋 Overview

| Feature | Category       | Description | Priority | Status |
| :--- |:---------------| :--- | :--- | :--- |
| **Chat Timeouts** | Moderation     | Temporarily restricts players from sending chat messages. | High | ✅ Implemented |
| **Vanish** | Administration | Makes staff members invisible to regular players. | High | ✅ Implemented |
| **Disguise** | Administration | Allows staff to disguise as another mob or player. | Medium | ✅ Implemented |
| **Freeze** | Moderation     | Immobilizes a player in place (e.g., during suspected hacking). | High | ✅ Implemented |
| **Nicknames** | Administration | Allows staff to change the name shown to other players to hide their identity. | Medium | ⏳ Planned |

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
  * Hides the staff member from the tab list, the locator bar and the in-game world.
  * Operators (gamemaster permission) can still see vanished staff; regular players cannot.
  * Regular players get a "left the game" message when someone vanishes and a "joined the game" message when they become visible again.
  * No entity collisions while vanished: vanished staff neither push nor get pushed by mobs, entities, boats or minecarts. Item pickup works normally.
  * Actionbar hint while vanished; state is kept on re-join, but reset on server restart.
  * Also available in the settings menu (`/maintena vanish`).
* **Known limitations:** the real join/leave messages when a vanished player actually disconnects or reconnects, `/list`, the server-list player count and tab-completion of names still reveal a vanished player.

### 3. Disguise ✅

* **Goal:** Transform into an entity, mob, or another player for testing, events, or moderation.
* **Commands:** `/disguise <player|entity>`, `/disguise player:<name>`, `/disguise status`, `/disguise list`, `/undisguise`
* **Features:**
  * Disguise as any Minecraft account, online or not (offline accounts are looked up at Mojang). Name and skin are replaced in the world, the tab list and the chat, with the team formatting of the imitated name.
  * Disguise as any mob, boss, vehicle, projectile or display entity. The disguise works on the real player: hits, damage and knockback still land on you and you keep moving like a player, even as a boat.
  * Sneaking, sprinting, swimming, burning and glowing are shown on the entity. Held items and armor are shown on mobs that can wear them.
  * Hitbox shrinks to fit the entity (never larger than the player's), hurt, death and idle sounds are those of the entity.
  * Fake "joined the game" / "left the game" messages when disguising and undisguising.
  * As an entity the staff member is removed from the tab list and the locator bar; chat messages are shown as normal chat lines.
  * Disguises are applied and removed live, without rejoining.
  * Name suggestions for online players, everybody who has been on the server before, and all entity IDs.
  * Every disguise and undisguise is written to the server log.
  * Also available in the settings menu (`/maintena disguise`).
* **Known limitations:** you still see yourself as a normal player, the real "left the game" message when a disguised player disconnects still appears, and item and experience orbs, paintings, item frames, leash knots, falling blocks, fishing bobbers, lightning bolts, area effect clouds and the ender dragon can't be used. A disguise ends on death or disconnect and is not saved across restarts.

### 4. Freeze ✅

* **Goal:** Lock players in place who are suspected of using unauthorized mods or cheats.
* **Commands:** `/freeze <player> [reason]`, `/freeze list`, `/unfreeze <player>`, `/unfreeze all`
* **Features:**
  * Prevents movement: the server holds the position and resets every attempt to move. Looking around still works.
  * Prevents interactions: breaking and using blocks, using items, and interacting with or attacking entities. Frozen players are removed from vehicles.
  * Optionally blocks chat as well as `/msg`, `/tell`, `/w`, `/me`, `/say` and `/teammsg` (off by default).
  * Displays instructions to the frozen player: a title, chat lines with the reason and a Discord hint (shown again after every re-join), and a regular actionbar reminder.
  * Frozen players take no damage (configurable).
  * The freeze is kept on re-join, but reset on server restart. Operators are notified when a frozen player logs out or joins again.
  * Safety checks: you can't freeze yourself, and operators can only be frozen from the console.
  * Texts and options are configurable in `config/maintena/freeze-config.json`.
  * Also available in the settings menu (`/maintena freeze`).
* **Known limitations:** commands of other mods (like `/home` or `/spawn`) are not blocked, the inventory is not locked, and if the server moves a frozen player, the new position becomes the new freeze point.

### 5. Nicknames

* **Goal:** Let staff change the name other players see (player tag) to hide their identity a little, without changing the real account name.
* **Suggested Commands:** `/nick <name>`, `/nick reset`
* **Features:**
  * Staff only (gamemaster permission).
  * Replaces the name above the head, in the tab list and in the chat.
  * Not persistent: a nickname is removed on disconnect and on server restart, so it is gone after a re-join.
  * Name checks: length limit, allowed characters, and no names of existing players.
  * Optional color codes in nicknames.
  * Every nickname change is written to the server log.
  * Also available in the settings menu (`/maintena nick`).