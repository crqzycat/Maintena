package crqzycat.maintena.restart;

import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class RestartManager {

    private static RestartManager instance;

    private RestartData data;
    private RestartData.Config config;
    private MinecraftServer server;

    private Timer tickTimer;
    private static final long TICK_INTERVAL = 1000;

    // Aktuell laufender Countdown, -1 = kein Restart angesetzt
    private long activeRestartTime = -1;
    private String activeScheduleId = null;
    private final Set<Integer> announcedMilestones = new HashSet<>();

    private static final DateTimeFormatter NEXT_TRIGGER_FORMAT =
            DateTimeFormatter.ofPattern("EEE HH:mm");

    private RestartManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadRestartData();
        this.config = PersistenceUtil.loadRestartConfig();

        recomputeMissingNextTriggers();
        startTicker();
    }

    public static void initialize(MinecraftServer server) {
        if (instance == null) {
            instance = new RestartManager(server);
        } else {
            instance.setServer(server);
        }
    }

    public static RestartManager getInstance() {
        if (instance == null) {
            instance = new RestartManager(null);
        }

        return instance;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Lädt restarts.json und restart-config.json neu von der Festplatte.
     */
    public void reload() {
        this.data = PersistenceUtil.loadRestartData();
        this.config = PersistenceUtil.loadRestartConfig();
        recomputeMissingNextTriggers();
    }

    // ==================== Manueller Restart ====================

    public void restartNow() {
        broadcastMessage(config.broadcastSaving);
        saveWorld();
        broadcastMessage(config.broadcastRestartNow);
        haltServer();
    }

    public void startManualCountdown(long minutes) {
        detachActiveSchedule();

        activeRestartTime = System.currentTimeMillis() + minutes * 60_000L;
        activeScheduleId = null;
        announcedMilestones.clear();
    }

    public boolean cancelActiveCountdown() {
        if (activeRestartTime == -1) {
            return false;
        }

        detachActiveSchedule();
        broadcastMessage(config.broadcastCancelled);

        activeRestartTime = -1;
        activeScheduleId = null;
        announcedMilestones.clear();

        return true;
    }

    public boolean isCountdownActive() {
        return activeRestartTime != -1;
    }

    /**
     * Wenn der aktuell laufende Countdown zu einem Schedule gehört (statt manuell zu sein),
     * wird dessen nextTrigger neu berechnet, damit die Regel nicht sofort wieder auslöst.
     */
    private void detachActiveSchedule() {
        if (activeScheduleId == null) {
            return;
        }

        RestartSchedule schedule = findSchedule(activeScheduleId);

        if (schedule != null) {
            schedule.nextTrigger = computeNextTrigger(schedule, System.currentTimeMillis());
            PersistenceUtil.saveRestartData(data);
        }
    }

    // ==================== Schedule-Verwaltung ====================

    public RestartSchedule addIntervalSchedule(long minutes, String name) {
        String id = resolveId(name);
        if (id == null) {
            return null;
        }

        RestartSchedule schedule = new RestartSchedule();
        schedule.id = id;
        schedule.type = RestartSchedule.Type.INTERVAL;
        schedule.intervalMinutes = minutes;
        schedule.nextTrigger = System.currentTimeMillis() + minutes * 60_000L;

        data.schedules.add(schedule);
        PersistenceUtil.saveRestartData(data);

        return schedule;
    }

    public RestartSchedule addTimeSchedule(
            int hour,
            int minute,
            RestartSchedule.Frequency frequency,
            DayOfWeek weekday,
            String name
    ) {
        String id = resolveId(name);
        if (id == null) {
            return null;
        }

        RestartSchedule schedule = new RestartSchedule();
        schedule.id = id;
        schedule.type = RestartSchedule.Type.TIME;
        schedule.hour = hour;
        schedule.minute = minute;
        schedule.frequency = frequency;
        schedule.weekday = weekday;
        schedule.nextTrigger = computeNextTrigger(schedule, System.currentTimeMillis());

        data.schedules.add(schedule);
        PersistenceUtil.saveRestartData(data);

        return schedule;
    }

    public boolean removeSchedule(String id) {
        boolean removed = data.schedules.removeIf(s -> s.id.equalsIgnoreCase(id));

        if (!removed) {
            return false;
        }

        if (id.equalsIgnoreCase(activeScheduleId)) {
            activeRestartTime = -1;
            activeScheduleId = null;
            announcedMilestones.clear();
        }

        PersistenceUtil.saveRestartData(data);
        return true;
    }

    public List<RestartSchedule> getSchedules() {
        return data.schedules;
    }

    public RestartSchedule findSchedule(String id) {
        if (id == null) {
            return null;
        }

        for (RestartSchedule schedule : data.schedules) {
            if (schedule.id.equalsIgnoreCase(id)) {
                return schedule;
            }
        }

        return null;
    }

    /**
     * Gibt die zu verwendende id zurück: den gewünschten Namen (falls frei) oder,
     * wenn keiner angegeben wurde, eine automatisch generierte id.
     * Gibt null zurück, wenn der gewünschte Name bereits vergeben ist.
     */
    private String resolveId(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return generateId();
        }

        return findSchedule(requestedName) == null ? requestedName : null;
    }

    private String generateId() {
        int i = 1;

        while (findSchedule("restart-" + i) != null) {
            i++;
        }

        return "restart-" + i;
    }

    public String getScheduleListText() {
        if (data.schedules.isEmpty()) {
            return config.scheduleListHeader + "\n" + config.scheduleListEmpty;
        }

        StringBuilder sb = new StringBuilder(config.scheduleListHeader);

        for (RestartSchedule schedule : data.schedules) {
            sb.append("\n§e- §b").append(schedule.id).append("§e: ")
                    .append(describeSchedule(schedule))
                    .append(" §7(next: ")
                    .append(formatNextTrigger(schedule.nextTrigger))
                    .append(")");

            if (schedule.id.equalsIgnoreCase(activeScheduleId)) {
                sb.append(" §c[counting down]");
            }
        }

        return sb.toString();
    }

    private String describeSchedule(RestartSchedule schedule) {
        if (schedule.type == RestartSchedule.Type.INTERVAL) {
            return "every " + schedule.intervalMinutes + " minute(s)";
        }

        String time = String.format("%02d:%02d", schedule.hour, schedule.minute);

        if (schedule.frequency == RestartSchedule.Frequency.DAILY) {
            return "daily at " + time;
        }

        return "every " + capitalize(schedule.weekday.name()) + " at " + time;
    }

    private String formatNextTrigger(long millis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(NEXT_TRIGGER_FORMAT);
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    // ==================== Tick-Loop ====================

    private void startTicker() {
        stopTicker();

        tickTimer = new Timer("RestartCheckTimer", true);

        tickTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        }, 0, TICK_INTERVAL);
    }

    private void stopTicker() {
        if (tickTimer != null) {
            tickTimer.cancel();
            tickTimer = null;
        }
    }

    private void tick() {
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (activeRestartTime != -1) {
            long remaining = activeRestartTime - now;

            if (remaining <= 0) {
                runOnServerThread(this::executeScheduledRestart);
            } else {
                checkWarnings(remaining);
            }

            return;
        }

        RestartSchedule next = findEarliestSchedule();
        if (next == null) {
            return;
        }

        long untilTrigger = next.nextTrigger - now;
        long maxWarningMinutes = config.warningMinutes.isEmpty()
                ? 0
                : Collections.max(config.warningMinutes);

        // Sobald wir innerhalb des größten Warnfensters sind, startet der Countdown für diese Regel
        if (untilTrigger <= maxWarningMinutes * 60_000L) {
            activeRestartTime = next.nextTrigger;
            activeScheduleId = next.id;
            announcedMilestones.clear();
        }
    }

    private RestartSchedule findEarliestSchedule() {
        RestartSchedule earliest = null;

        for (RestartSchedule schedule : data.schedules) {
            if (earliest == null || schedule.nextTrigger < earliest.nextTrigger) {
                earliest = schedule;
            }
        }

        return earliest;
    }

    private void checkWarnings(long remainingMillis) {
        long remainingMinutes = remainingMillis / 60_000L;

        for (int milestone : config.warningMinutes) {
            if (remainingMinutes <= milestone && !announcedMilestones.contains(milestone)) {
                announcedMilestones.add(milestone);

                String message = config.broadcastWarning
                        .replace("%time%", String.valueOf(milestone));

                runOnServerThread(() -> broadcastMessage(message));
            }
        }
    }

    private void executeScheduledRestart() {
        broadcastMessage(config.broadcastSaving);
        saveWorld();
        broadcastMessage(config.broadcastRestartNow);

        String finishedScheduleId = activeScheduleId;

        activeRestartTime = -1;
        activeScheduleId = null;
        announcedMilestones.clear();

        if (finishedScheduleId != null) {
            RestartSchedule schedule = findSchedule(finishedScheduleId);

            if (schedule != null) {
                schedule.nextTrigger = computeNextTrigger(schedule, System.currentTimeMillis());
                PersistenceUtil.saveRestartData(data);
            }
        }

        haltServer();
    }

    /**
     * Berechnet den nächsten Auslösezeitpunkt einer Regel, ausgehend von afterMillis.
     */
    private long computeNextTrigger(RestartSchedule schedule, long afterMillis) {
        if (schedule.type == RestartSchedule.Type.INTERVAL) {
            return afterMillis + schedule.intervalMinutes * 60_000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime after = Instant.ofEpochMilli(afterMillis).atZone(zone);
        ZonedDateTime candidate = after
                .withHour(schedule.hour)
                .withMinute(schedule.minute)
                .withSecond(0)
                .withNano(0);

        if (schedule.frequency == RestartSchedule.Frequency.DAILY) {
            if (!candidate.isAfter(after)) {
                candidate = candidate.plusDays(1);
            }

            return candidate.toInstant().toEpochMilli();
        }

        // WEEKLY
        while (candidate.getDayOfWeek() != schedule.weekday || !candidate.isAfter(after)) {
            candidate = candidate.plusDays(1);
        }

        return candidate.toInstant().toEpochMilli();
    }

    /**
     * Falls Regeln ohne gültigen nextTrigger geladen werden (z.B. manuell editierte config),
     * wird dieser frisch berechnet.
     */
    private void recomputeMissingNextTriggers() {
        boolean changed = false;

        for (RestartSchedule schedule : data.schedules) {
            if (schedule.nextTrigger <= 0) {
                schedule.nextTrigger = computeNextTrigger(schedule, System.currentTimeMillis());
                changed = true;
            }
        }

        if (changed) {
            PersistenceUtil.saveRestartData(data);
        }
    }

    // ==================== Server-Aktionen ====================

    private void saveWorld() {
        if (server == null) {
            return;
        }

        server.saveEverything(false, true, false);
    }

    private void haltServer() {
        if (server == null) {
            return;
        }

        server.halt(false);
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
}
