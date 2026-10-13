package crqzycat.maintena.maintenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class MaintenanceData {
    // volatile bzw. concurrent: werden auch vom Netzwerk-Thread gelesen (Server-Liste/MOTD)
    public volatile boolean enabled = false;
    public final List<WhitelistEntry> whitelist = new CopyOnWriteArrayList<>();
    public volatile long endTime = -1; // -1 = no auto-stop set

    /**
     * Ein Whitelist-Eintrag. Geprüft wird über die UUID, der Name dient nur der Anzeige.
     * Alte Einträge aus früheren Versionen haben noch keine UUID (uuid == null); sie werden
     * über usercache.json oder beim ersten Login des Spielers an dessen UUID gebunden.
     */
    public static class WhitelistEntry {
        public volatile String name;
        public volatile String uuid; // null = alter Eintrag, nur Name bekannt

        public WhitelistEntry(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    public static class Config {
        public String broadcastStart = "§6[§cMaintenance§6] §cServer entering maintenance mode.";
        public String broadcastEnd = "§6[§cMaintenance§6] §aServer maintenance complete!";
        public String broadcastWarning = "§6[§cMaintenance§6] §eServer will exit maintenance in §b%time% §eminutes.";
        public String statusMessage = "§6=== Maintenance Status ===\n" +
                "§eEnabled: §b%enabled%\n" +
                "§eWhitelisted Players: §b%players%\n" +
                "§eEnd Time: §b%endtime%";

        // Shown in the server list (multiplayer screen) MOTD while maintenance is active
        public String motdMaintenanceLine = "§c⚠ Under Maintenance";
        public String motdMaintenanceTimed = "§eBack online in §b%time%";
        public String motdMaintenanceNoTime = "§eNo estimated time given";
    }

    // ==================== Whitelist ====================

    /** Nur die Namen, für Anzeige und Tab-Vervollständigung. */
    public List<String> getWhitelistNames() {
        List<String> names = new ArrayList<>();

        for (WhitelistEntry entry : whitelist) {
            names.add(entry.name);
        }

        return names;
    }

    /** Fügt einen Spieler hinzu (oder aktualisiert Name/UUID eines vorhandenen Eintrags). */
    public void addWhitelistedPlayer(String name, UUID id) {
        String uuid = id == null ? null : id.toString();

        for (WhitelistEntry entry : whitelist) {
            boolean sameUuid = uuid != null && uuid.equalsIgnoreCase(entry.uuid);
            boolean sameName = entry.name.equalsIgnoreCase(name) && (entry.uuid == null || uuid == null);

            if (sameUuid || sameName) {
                entry.name = name;

                if (uuid != null) {
                    entry.uuid = uuid;
                }

                return;
            }
        }

        whitelist.add(new WhitelistEntry(name, uuid));
    }

    /** Entfernt alle Einträge mit diesem Namen. */
    public void removeWhitelistedPlayer(String name) {
        whitelist.removeIf(entry -> entry.name.equalsIgnoreCase(name));
    }

    /**
     * Steht der Spieler auf der Whitelist? Einträge mit UUID zählen nur bei gleicher UUID
     * (ein anderer Account mit demselben Namen kommt nicht durch). Alte Einträge ohne UUID
     * zählen noch über den Namen.
     */
    public boolean isWhitelisted(UUID id, String name) {
        String uuid = id.toString();

        for (WhitelistEntry entry : whitelist) {
            if (entry.uuid != null) {
                if (entry.uuid.equalsIgnoreCase(uuid)) {
                    return true;
                }
            } else if (entry.name.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Beim Login: bindet einen alten Eintrag an die UUID und zieht bei einem Namenswechsel
     * den gespeicherten Namen nach.
     *
     * @return true, wenn sich ein Eintrag geändert hat (dann muss gespeichert werden)
     */
    public boolean refreshEntry(UUID id, String name) {
        String uuid = id.toString();
        boolean changed = false;

        for (WhitelistEntry entry : whitelist) {
            if (entry.uuid == null) {
                if (entry.name.equalsIgnoreCase(name)) {
                    entry.uuid = uuid;
                    entry.name = name;
                    changed = true;
                }
            } else if (entry.uuid.equalsIgnoreCase(uuid) && !entry.name.equals(name)) {
                entry.name = name;
                changed = true;
            }
        }

        return changed;
    }

    public boolean hasLegacyEntries() {
        for (WhitelistEntry entry : whitelist) {
            if (entry.uuid == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Bindet alte Einträge (nur Name) anhand einer Name-zu-UUID-Zuordnung (usercache.json).
     *
     * @param knownIds Name (Groß-/Kleinschreibung egal) zu UUID
     * @return true, wenn mindestens ein Eintrag gebunden wurde
     */
    public boolean bindLegacyEntries(Map<String, UUID> knownIds) {
        boolean changed = false;

        for (WhitelistEntry entry : whitelist) {
            if (entry.uuid != null) {
                continue;
            }

            UUID id = knownIds.get(entry.name.toLowerCase(Locale.ROOT));

            if (id != null) {
                entry.uuid = id.toString();
                changed = true;
            }
        }

        return changed;
    }

    public void clearWhitelist() {
        whitelist.clear();
    }

    // ==================== Zeit ====================

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getTimeRemaining() {
        if (endTime == -1) return -1;
        return endTime - System.currentTimeMillis();
    }

    public boolean isTimeExpired() {
        if (endTime == -1) return false;
        return System.currentTimeMillis() >= endTime;
    }

    public void clearEndTime() {
        endTime = -1;
    }
}
