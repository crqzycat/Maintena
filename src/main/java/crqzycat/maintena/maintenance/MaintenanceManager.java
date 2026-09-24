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
}