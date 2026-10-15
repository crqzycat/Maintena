package crqzycat.maintena.restart;

import crqzycat.maintena.util.PersistenceUtil;
import crqzycat.maintena.util.ScheduleUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RestartManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    /** Maintena stoppt den Server nur. Wieder gestartet wird er von außen (Skript, Dienst, Docker). */
    private static final String RESTART_HINT =
            "The server only stops, a start script or service has to start it again.";

    private static RestartManager instance;

    private volatile RestartData data;
    private volatile RestartData.Config config;
    private volatile MinecraftServer server;

    // Aktuell laufender Countdown, -1 = kein Restart angesetzt
    private long activeRestartTime = -1;
    private String activeScheduleId = null;
    private final Set<Integer> announcedMilestones = new HashSet<>();

    private RestartManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadRestartData();
        this.config = PersistenceUtil.loadRestartConfig();

        recomputeMissingNextTriggers();
    }

    public static RestartManager getInstance() {
        if (instance == null) {
            instance = new RestartManager(null);
        }

        return instance;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;

        if (!data.schedules.isEmpty()) {
            LOGGER.info("[Restart] Restart schedules are set. {}", RESTART_HINT);
        }
    }

    /**
     * Beim Stoppen des Servers: laufenden Countdown verwerfen und die Server-Referenz
     * freigeben. Die gespeicherten Zeitpläne bleiben erhalten.
     */
    public void shutdown() {
        server = null;
        activeRestartTime = -1;
        activeScheduleId = null;
        announcedMilestones.clear();
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
        skipMilestonesAbove(minutes);
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

        sb.append("\n§7").append(RESTART_HINT);

        return sb.toString();
    }

    public String describeSchedule(RestartSchedule schedule) {
        return ScheduleUtil.describe(
                schedule.type == RestartSchedule.Type.INTERVAL, schedule.intervalMinutes,
                schedule.hour, schedule.minute,
                schedule.frequency == RestartSchedule.Frequency.DAILY, schedule.weekday);
    }

    private String formatNextTrigger(long millis) {
        return ScheduleUtil.formatNextTrigger(millis);
    }

    // ==================== Tick-Loop ====================

    /** Wird jede Sekunde vom Server-Tick aufgerufen (Server-Thread). */
    public void tick() {
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (activeRestartTime != -1) {
            long remaining = activeRestartTime - now;

            if (remaining <= 0) {
                executeScheduledRestart();
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

            // Aufrunden, damit die aktuell zutreffende Schwelle nicht versehentlich mit übersprungen wird
            long totalMinutes = (untilTrigger + 59_999L) / 60_000L;
            skipMilestonesAbove(totalMinutes);
        }
    }

    /**
     * Markiert alle Warnschwellen, die über der tatsächlichen Countdown-Dauer liegen,
     * als "bereits angesagt", damit sie beim Start nicht alle auf einmal gebroadcastet werden.
     * Bsp.: "restart in 1" -> nur die 1-Minuten-Warnung wird gezeigt, nicht 30/15/10/5/3/2/1 gleichzeitig.
     */
    private void skipMilestonesAbove(long totalMinutes) {
        for (int milestone : config.warningMinutes) {
            if (milestone > totalMinutes) {
                announcedMilestones.add(milestone);
            }
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

                broadcastMessage(message);
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
        return ScheduleUtil.nextTrigger(
                schedule.type == RestartSchedule.Type.INTERVAL, schedule.intervalMinutes,
                schedule.hour, schedule.minute,
                schedule.frequency == RestartSchedule.Frequency.DAILY, schedule.weekday, afterMillis);
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
}
