package crqzycat.maintena.maintenance;

import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MaintenanceManager {

    private static MaintenanceManager instance;

    private MaintenanceData data;
    private MaintenanceData.Config config;
    private MinecraftServer server;

    private Timer checkTimer;
    private long lastWarningTime = 0;

    private static final long WARNING_INTERVAL = 5 * 60 * 1000;
    private static final long CHECK_INTERVAL = 1000;

    private MaintenanceManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadData();
        this.config = PersistenceUtil.loadConfig();

        if (server != null) {
            addOpsToWhitelist();
        }

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

    private void addOpsToWhitelist() {
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.nameAndId())) {
                addWhitelistedPlayer(player.getName().getString());
            }
        }
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

            if (!isOp && !isWhitelisted(player.getName().getString())) {
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

    public void addWhitelistedPlayer(String player) {
        data.addWhitelistedPlayer(player);
        PersistenceUtil.saveData(data);
    }

    public void removeWhitelistedPlayer(String player) {
        data.removeWhitelistedPlayer(player);
        PersistenceUtil.saveData(data);
    }

    public boolean isWhitelisted(String player) {
        return data.isWhitelisted(player);
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

        String playersStr = data.whitelistedPlayers.isEmpty()
                ? "None"
                : String.join(", ", data.whitelistedPlayers);

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

        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (data.enabled && data.isTimeExpired()) {
                    runOnServerThread(() -> {
                        if (data.enabled && data.isTimeExpired()) {
                            disable();
                        }
                    });
                    return;
                }

                if (data.enabled && data.endTime != -1) {
                    long timeRemaining = data.getTimeRemaining();
                    long minutesRemaining = timeRemaining / (60 * 1000);

                    if (minutesRemaining <= 30 && minutesRemaining > 0) {
                        long timeSinceLastWarning =
                                System.currentTimeMillis()
                                        - lastWarningTime;

                        if (timeSinceLastWarning >= WARNING_INTERVAL) {
                            String warningMsg = config.broadcastWarning
                                    .replace(
                                            "%time%",
                                            String.valueOf(minutesRemaining)
                                    );

                            runOnServerThread(() -> {
                                if (data.enabled) {
                                    broadcastMessage(warningMsg);
                                }
                            });

                            lastWarningTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }, 0, CHECK_INTERVAL);
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

    private void runOnServerThread(Runnable task) {
        if (server != null) {
            server.execute(task);
        }
    }

    public void setServer(MinecraftServer server) {
        this.server = server;

        addOpsToWhitelist();

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
        java.io.File usercacheFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir()
                .resolve("usercache.json")
                .toFile();

        if (usercacheFile.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(usercacheFile)) {
                com.google.gson.JsonArray entries =
                        com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();

                for (com.google.gson.JsonElement element : entries) {
                    com.google.gson.JsonObject entry = element.getAsJsonObject();

                    if (entry.has("name")) {
                        allPlayers.add(entry.get("name").getAsString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return allPlayers;
    }

    /**
     * Gibt nur die Spieler zurück, die auf der Whitelist sind
     */
    public java.util.Collection<String> getWhitelistedPlayers() {
        return new java.util.ArrayList<>(data.whitelistedPlayers);
    }
}