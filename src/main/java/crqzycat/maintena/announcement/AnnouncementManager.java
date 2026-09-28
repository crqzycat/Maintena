package crqzycat.maintena.announcement;

import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AnnouncementManager {

    private static AnnouncementManager instance;

    private AnnouncementData data;
    private AnnouncementData.Config config;
    private volatile MinecraftServer server;

    private Timer tickTimer;
    private static final long TICK_INTERVAL = 1000;

    // War der Server länger offline als dieses Fenster, wird eine verpasste Announcement
    // nicht nachgeholt, sondern nur der nächste Zeitpunkt berechnet.
    private static final long MISSED_GRACE_MILLIS = 60_000L;

    private static final DateTimeFormatter NEXT_TRIGGER_FORMAT =
            DateTimeFormatter.ofPattern("EEE HH:mm");

    private AnnouncementManager(MinecraftServer server) {
        this.server = server;
        this.data = PersistenceUtil.loadAnnouncementData();
        this.config = PersistenceUtil.loadAnnouncementConfig();

        recomputeMissingNextTriggers();
        startTicker();
    }

    public static AnnouncementManager getInstance() {
        if (instance == null) {
            instance = new AnnouncementManager(null);
        }

        return instance;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Lädt announcements.json und announcement-config.json neu von der Festplatte.
     */
    public void reload() {
        this.data = PersistenceUtil.loadAnnouncementData();
        this.config = PersistenceUtil.loadAnnouncementConfig();
        recomputeMissingNextTriggers();
    }

    // ==================== Einmalige Announcement (ohne Schedule) ====================

    /**
     * Sendet eine Announcement sofort an alle Spieler. Wird nicht gespeichert.
     */
    public void send(AnnouncementSchedule.Display display, String message) {
        if (server == null) {
            return;
        }

        if (display == AnnouncementSchedule.Display.SCREEN) {
            sendScreen(message);
        } else {
            sendChat(message);
        }
    }

    /**
     * Wandelt "&a", "&l" usw. in Farbcodes (§) um, damit man sie im Command tippen kann.
     * Nur Kleinbuchstaben, damit z.B. "Q&A" nicht versehentlich gefärbt wird.
     */
    public static String colorize(String text) {
        return text.replaceAll("&([0-9a-fk-or])", "§$1");
    }

    // ==================== Schedule-Verwaltung ====================

    public AnnouncementSchedule addIntervalSchedule(
            long minutes,
            AnnouncementSchedule.Display display,
            String name,
            String message
    ) {
        if (findSchedule(name) != null) {
            return null;
        }

        AnnouncementSchedule schedule = new AnnouncementSchedule();
        schedule.id = name;
        schedule.message = message;
        schedule.display = display;
        schedule.type = AnnouncementSchedule.Type.INTERVAL;
        schedule.intervalMinutes = minutes;
        schedule.nextTrigger = System.currentTimeMillis() + minutes * 60_000L;

        data.schedules.add(schedule);
        PersistenceUtil.saveAnnouncementData(data);

        return schedule;
    }

    public AnnouncementSchedule addTimeSchedule(
            int hour,
            int minute,
            AnnouncementSchedule.Frequency frequency,
            DayOfWeek weekday,
            AnnouncementSchedule.Display display,
            String name,
            String message
    ) {
        if (findSchedule(name) != null) {
            return null;
        }

        AnnouncementSchedule schedule = new AnnouncementSchedule();
        schedule.id = name;
        schedule.message = message;
        schedule.display = display;
        schedule.type = AnnouncementSchedule.Type.TIME;
        schedule.hour = hour;
        schedule.minute = minute;
        schedule.frequency = frequency;
        schedule.weekday = weekday;
        schedule.nextTrigger = computeNextTrigger(schedule, System.currentTimeMillis());

        data.schedules.add(schedule);
        PersistenceUtil.saveAnnouncementData(data);

        return schedule;
    }

    public boolean removeSchedule(String id) {
        boolean removed = data.schedules.removeIf(s -> s.id.equalsIgnoreCase(id));

        if (removed) {
            PersistenceUtil.saveAnnouncementData(data);
        }

        return removed;
    }

    public List<AnnouncementSchedule> getSchedules() {
        return data.schedules;
    }

    public AnnouncementSchedule findSchedule(String id) {
        if (id == null) {
            return null;
        }

        for (AnnouncementSchedule schedule : data.schedules) {
            if (schedule.id.equalsIgnoreCase(id)) {
                return schedule;
            }
        }

        return null;
    }

    public String getScheduleListText() {
        if (data.schedules.isEmpty()) {
            return config.scheduleListHeader + "\n" + config.scheduleListEmpty;
        }

        StringBuilder sb = new StringBuilder(config.scheduleListHeader);

        for (AnnouncementSchedule schedule : data.schedules) {
            sb.append("\n§e- §b").append(schedule.id).append("§e: ")
                    .append(describeSchedule(schedule))
                    .append(" §7[").append(schedule.display.name().toLowerCase()).append("]")
                    .append(" (next: ").append(formatNextTrigger(schedule.nextTrigger)).append(")")
                    .append("\n    §f").append(preview(schedule.message));
        }

        return sb.toString();
    }

    public String describeSchedule(AnnouncementSchedule schedule) {
        if (schedule.type == AnnouncementSchedule.Type.INTERVAL) {
            long hours = schedule.intervalMinutes / 60;
            long minutes = schedule.intervalMinutes % 60;

            if (hours == 0) {
                return "every " + minutes + "m";
            }

            if (minutes == 0) {
                return "every " + hours + "h";
            }

            return "every " + hours + "h " + minutes + "m";
        }

        String time = String.format("%02d:%02d", schedule.hour, schedule.minute);

        if (schedule.frequency == AnnouncementSchedule.Frequency.DAILY) {
            return "daily at " + time;
        }

        return "every " + capitalize(schedule.weekday.name()) + " at " + time;
    }

    private String formatNextTrigger(long millis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(NEXT_TRIGGER_FORMAT);
    }

    private static String preview(String message) {
        return message.length() <= 50 ? message : message.substring(0, 47) + "...";
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    // ==================== Tick-Loop ====================

    private void startTicker() {
        stopTicker();

        tickTimer = new Timer("AnnouncementCheckTimer", true);

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
        MinecraftServer current = server;

        if (current == null) {
            return;
        }

        // Alles Weitere läuft auf dem Server-Thread (gleicher Thread wie die Commands)
        current.execute(this::processDue);
    }

    private void processDue() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (AnnouncementSchedule schedule : data.schedules) {
            if (schedule.nextTrigger > now) {
                continue;
            }

            boolean missed = now - schedule.nextTrigger > MISSED_GRACE_MILLIS;

            if (!missed) {
                send(schedule.display, schedule.message);
            }

            schedule.nextTrigger = computeNextTrigger(schedule, now);
            changed = true;
        }

        if (changed) {
            PersistenceUtil.saveAnnouncementData(data);
        }
    }

    /**
     * Berechnet den nächsten Auslösezeitpunkt einer Regel, ausgehend von afterMillis.
     */
    private long computeNextTrigger(AnnouncementSchedule schedule, long afterMillis) {
        if (schedule.type == AnnouncementSchedule.Type.INTERVAL) {
            return afterMillis + schedule.intervalMinutes * 60_000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime after = Instant.ofEpochMilli(afterMillis).atZone(zone);
        ZonedDateTime candidate = after
                .withHour(schedule.hour)
                .withMinute(schedule.minute)
                .withSecond(0)
                .withNano(0);

        if (schedule.frequency == AnnouncementSchedule.Frequency.DAILY) {
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
     * Falls Regeln ohne gültigen nextTrigger geladen werden (z.B. manuell editierte Datei
     * oder Server war länger offline), wird dieser frisch berechnet.
     */
    private void recomputeMissingNextTriggers() {
        boolean changed = false;
        long now = System.currentTimeMillis();

        for (AnnouncementSchedule schedule : data.schedules) {
            if (schedule.display == null) {
                schedule.display = AnnouncementSchedule.Display.CHAT;
                changed = true;
            }

            if (schedule.nextTrigger <= 0) {
                schedule.nextTrigger = computeNextTrigger(schedule, now);
                changed = true;
            }
        }

        if (changed) {
            PersistenceUtil.saveAnnouncementData(data);
        }
    }

    // ==================== Anzeige ====================

    private void sendChat(String message) {
        Component text = Component.literal(config.chatFormat.replace("%message%", message));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(text);
        }
    }

    /**
     * Zeigt die Announcement groß mitten auf dem Bildschirm (Title).
     * Mit " | " im Text lässt sich ein kleinerer Untertitel abtrennen: "Titel | Untertitel".
     */
    private void sendScreen(String message) {
        String[] parts = message.split("\\s*\\|\\s*", 2);
        Component title = Component.literal(parts[0]);
        Component subtitle = parts.length > 1 ? Component.literal(parts[1]) : Component.empty();

        ClientboundSetTitlesAnimationPacket timing = new ClientboundSetTitlesAnimationPacket(
                config.screenFadeInTicks,
                config.screenStayTicks,
                config.screenFadeOutTicks
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(timing);
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
}
