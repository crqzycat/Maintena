package crqzycat.maintena.maintenance;

import crqzycat.maintena.util.PersistenceUtil;
import crqzycat.maintena.util.UsercacheCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class MaintenanceManager {

    private static MaintenanceManager instance;

    // volatile: der Netzwerk-Thread liest (Server-Liste), der Server-Thread ersetzt
    private volatile MaintenanceData data;
    private volatile MaintenanceData.Config config;
    private volatile MinecraftServer server;

    private long lastWarningTime = 0; // nur Server-Thread

    private static final long WARNING_INTERVAL = 5 * 60 * 1000;

    private MaintenanceManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadData();
        this.config = PersistenceUtil.loadConfig();

        bindLegacyWhitelistEntries();
    }

    public static MaintenanceManager getInstance() {
        if (instance == null) {
            instance = new MaintenanceManager(null);
        }

        return instance;
    }

    public void enable(long durationMinutes) {
        data.enabled = true;

        if (durationMinutes > 0) {
            long endTime = System.currentTimeMillis()
                    + (durationMinutes * 60 * 1000);

            data.setEndTime(endTime);
        } else {
            data.clearEndTime();
        }

        PersistenceUtil.saveData(data);

        if (server != null) {
            broadcastMessage(config.broadcastStart);
            kickNonWhitelistedPlayers();
        }
    }

    /**
     * Kickt alle aktuell online Spieler, die weder auf der Whitelist stehen
     * noch OP sind. Wird beim Einschalten der Wartung aufgerufen.
     */
    private void kickNonWhitelistedPlayers() {
        if (server == null) {
            return;
        }

        Component kickMessage = getKickMessage();

        if (kickMessage == null) {
            return;
        }

        // Kopie der Liste, da disconnect() die Original-Liste während der Iteration verändert
        for (ServerPlayer player : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            boolean isOp = server.getPlayerList().isOp(player.nameAndId());

            if (!isOp && !isWhitelisted(player.nameAndId().id(), player.nameAndId().name())) {
                player.connection.disconnect(kickMessage);
            }
        }
    }

    public void disable() {
        data.enabled = false;

        data.clearEndTime();

        PersistenceUtil.saveData(data);

        if (server != null) {
            broadcastMessage(config.broadcastEnd);
        }
    }

    /**
     * Lädt config.json und maintenance.json neu von der Festplatte,
     * ohne den Server neu zu starten.
     */
    public void reload() {
        this.data = PersistenceUtil.loadData();
        this.config = PersistenceUtil.loadConfig();

        bindLegacyWhitelistEntries();
    }

    public void clearWhitelist() {
        data.clearWhitelist();
        PersistenceUtil.saveData(data);
    }

    public void addWhitelistedPlayer(String name, UUID id) {
        data.addWhitelistedPlayer(name, id);
        PersistenceUtil.saveData(data);
    }

    public void removeWhitelistedPlayer(String player) {
        data.removeWhitelistedPlayer(player);
        PersistenceUtil.saveData(data);
    }

    /**
     * Prüft die Whitelist über die UUID. Alte Einträge (nur Name) werden dabei an die UUID
     * gebunden, und ein geänderter Spielername wird im Eintrag nachgezogen.
     */
    public boolean isWhitelisted(UUID id, String name) {
        MaintenanceData current = data;

        if (!current.isWhitelisted(id, name)) {
            return false;
        }

        if (current.refreshEntry(id, name)) {
            PersistenceUtil.saveData(current);
        }

        return true;
    }

    /**
     * Alte Whitelist-Einträge (nur Name, aus früheren Versionen) anhand von usercache.json an
     * die UUID binden. Was dort nicht steht, wird beim ersten Login des Spielers gebunden.
     */
    private void bindLegacyWhitelistEntries() {
        MaintenanceData current = data;

        if (!current.hasLegacyEntries()) {
            return;
        }

        if (current.bindLegacyEntries(UsercacheCache.get())) {
            PersistenceUtil.saveData(current);
        }
    }

    public boolean isEnabled() {
        return data.enabled;
    }

    public MaintenanceData getData() {
        return data;
    }

    public MaintenanceData.Config getConfig() {
        return config;
    }

    public String getStatus() {
        String enabledStr = data.enabled
                ? "§a✓ Enabled"
                : "§c✗ Disabled";

        java.util.List<String> whitelistNames = data.getWhitelistNames();

        String playersStr = whitelistNames.isEmpty()
                ? "None"
                : String.join(", ", whitelistNames);

        String endTimeStr = data.endTime == -1
                ? "§eManual stop"
                : formatEndTime(data.endTime);

        return config.statusMessage
                .replace("%enabled%", enabledStr)
                .replace("%players%", playersStr)
                .replace("%endtime%", endTimeStr);
    }

    /**
     * Baut den MOTD-Text für die Server-Liste (Multiplayer-Screen).
     * Gibt null zurück, wenn keine Wartung läuft (dann bleibt der normale MOTD unverändert).
     */
    public String getMaintenanceMotd() {
        if (!data.enabled) {
            return null;
        }

        return config.motdMaintenanceLine + "\n" + buildTimeLine();
    }

    /**
     * Baut die Meldung, die ein abgewiesener oder gekickter Spieler sieht (eigener Text aus
     * config.json, "kickMessage"). Gibt null zurück, wenn keine Wartung läuft.
     */
    public Component getKickMessage() {
        if (!data.enabled) {
            return null;
        }

        return Component.literal(config.kickMessage.replace("%info%", buildTimeLine()));
    }

    /** Die Zeile mit der Restzeit ("Back online in 12:30") bzw. dem Hinweis, dass es keine gibt. */
    private String buildTimeLine() {
        if (data.endTime == -1) {
            return config.motdMaintenanceNoTime;
        }

        long remaining = data.getTimeRemaining();

        if (remaining <= 0) {
            return config.motdMaintenanceNoTime;
        }

        long minutes = remaining / (60 * 1000);
        long seconds = (remaining % (60 * 1000)) / 1000;
        String timeStr = String.format("%d:%02d", minutes, seconds);

        return config.motdMaintenanceTimed.replace("%time%", timeStr);
    }

    private String formatEndTime(long endTime) {
        long remaining = endTime - System.currentTimeMillis();

        if (remaining <= 0) {
            return "§cExpired";
        }

        long minutes = remaining / (60 * 1000);
        long seconds = (remaining % (60 * 1000)) / 1000;

        return String.format("§b%d:%02d", minutes, seconds);
    }

    /**
     * Wird jede Sekunde vom Server-Tick aufgerufen (Server-Thread): beendet die Wartung, wenn die
     * Zeit abgelaufen ist, und warnt in den letzten 30 Minuten alle 5 Minuten.
     */
    public void tick() {
        if (server == null || !data.enabled || data.endTime == -1) {
            return;
        }

        if (data.isTimeExpired()) {
            disable();
            return;
        }

        long minutesRemaining = data.getTimeRemaining() / (60 * 1000);

        if (minutesRemaining <= 30 && minutesRemaining > 0) {
            long now = System.currentTimeMillis();

            if (now - lastWarningTime >= WARNING_INTERVAL) {
                broadcastMessage(config.broadcastWarning
                        .replace("%time%", String.valueOf(minutesRemaining)));

                lastWarningTime = now;
            }
        }
    }

    private void broadcastMessage(String message) {
        if (server == null) {
            return;
        }

        Component textMessage = Component.literal(message);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(textMessage);
        }
    }

    /**
     * Beim Stoppen des Servers: die Server-Referenz freigeben (sonst hält der Manager den alten
     * Server im Speicher, z.B. beim Integrated Server). Der Wartungsstatus bleibt gespeichert.
     */
    public void shutdown() {
        server = null;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;

        bindLegacyWhitelistEntries();
    }

    /**
     * Gibt alle bekannten Spielernamen zurück (online und offline)
     * Dies werden alle Spieler sein, die jemals auf den Server gejoined sind
     */
    public java.util.Collection<String> getAllPlayerNames() {
        java.util.Set<String> allPlayers = new java.util.HashSet<>();
        
        // Online Spieler hinzufügen
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                allPlayers.add(player.getName().getString());
            }
        }
        
        // usercache.json enthält Name + UUID von jedem Spieler, der jemals gejoint ist
        // (auch offline Spieler) - die playerdata-Dateien sind immer nach UUID benannt,
        // daher lässt sich der Name daraus nicht rekonstruieren.
        allPlayers.addAll(UsercacheCache.get().keySet());

        return allPlayers;
    }

    /**
     * Gibt nur die Spieler zurück, die auf der Whitelist sind
     */
    public java.util.Collection<String> getWhitelistedPlayers() {
        return data.getWhitelistNames();
    }
}