package crqzycat.maintena.maintenance;

import crqzycat.maintena.util.PersistenceUtil;
import crqzycat.maintena.util.UsercacheCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MaintenanceManager {

    private static MaintenanceManager instance;

    // volatile: werden vom Timer- und Netzwerk-Thread gelesen, vom Server-Thread ersetzt
    private volatile MaintenanceData data;
    private volatile MaintenanceData.Config config;
    private volatile MinecraftServer server;

    private Timer checkTimer;
    private long lastWarningTime = 0; // nur Server-Thread
    private final AtomicBoolean tickPending = new AtomicBoolean(false);

    private static final long WARNING_INTERVAL = 5 * 60 * 1000;
    private static final long CHECK_INTERVAL = 1000;

    private MaintenanceManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadData();
        this.config = PersistenceUtil.loadConfig();

        bindLegacyWhitelistEntries();

        if (data.enabled && data.endTime != -1) {
            startAutoStopTimer();
        }
    }

    public static void initialize(MinecraftServer server) {
        if (instance == null) {
            instance = new MaintenanceManager(server);
        } else {
            instance.setServer(server);
        }
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
            startAutoStopTimer();
        } else {
            stopAutoStopTimer();
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

        Component kickMessage = Component.literal(getMaintenanceMotd());

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

        stopAutoStopTimer();
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

        if (data.enabled && data.endTime != -1) {
            startAutoStopTimer();
        } else {
            stopAutoStopTimer();
        }
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

        String line2;

        if (data.endTime == -1) {
            line2 = config.motdMaintenanceNoTime;
        } else {
            long remaining = data.getTimeRemaining();

            if (remaining <= 0) {
                line2 = config.motdMaintenanceNoTime;
            } else {
                long minutes = remaining / (60 * 1000);
                long seconds = (remaining % (60 * 1000)) / 1000;
                String timeStr = String.format("%d:%02d", minutes, seconds);

                line2 = config.motdMaintenanceTimed.replace("%time%", timeStr);
            }
        }

        return config.motdMaintenanceLine + "\n" + line2;
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

    private void startAutoStopTimer() {
        stopAutoStopTimer();

        checkTimer = new Timer("MaintenanceCheckTimer", true);

        // Der Timer-Thread fasst selbst keinen Zustand an, er stößt nur den Check auf dem
        // Server-Thread an (dort laufen auch die Commands). So gibt es keine Race Conditions.
        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                MinecraftServer current = server;

                if (current == null || !tickPending.compareAndSet(false, true)) {
                    return;
                }

                current.execute(() -> {
                    tickPending.set(false);
                    checkTimerTick();
                });
            }
        }, 0, CHECK_INTERVAL);
    }

    /** Läuft auf dem Server-Thread. */
    private void checkTimerTick() {
        if (!data.enabled || data.endTime == -1) {
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

    private void stopAutoStopTimer() {
        if (checkTimer != null) {
            checkTimer.cancel();
            checkTimer = null;
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
     * Beim Stoppen des Servers: Timer anhalten und die Server-Referenz freigeben (sonst läuft der
     * Timer-Thread weiter und hält den alten Server im Speicher, z.B. beim Integrated Server).
     * Der Wartungsstatus selbst bleibt gespeichert.
     */
    public void shutdown() {
        stopAutoStopTimer();
        tickPending.set(false);
        server = null;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;

        bindLegacyWhitelistEntries();

        if (data.enabled && data.endTime != -1) {
            startAutoStopTimer();
        }
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