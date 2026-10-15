package crqzycat.maintena.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Gemeinsame Zeitplan-Logik für Restarts und Announcements. Beide haben denselben Aufbau
 * (INTERVAL = alle X Minuten, TIME = feste Uhrzeit täglich oder wöchentlich), deshalb gibt es
 * die Berechnung und die Textausgabe nur einmal.
 */
public final class ScheduleUtil {

    private static final DateTimeFormatter NEXT_TRIGGER_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm");

    private ScheduleUtil() {
    }

    /**
     * Berechnet den nächsten Auslösezeitpunkt einer Regel, ausgehend von afterMillis.
     *
     * @param interval        true = alle intervalMinutes Minuten, false = feste Uhrzeit
     * @param intervalMinutes nur bei interval
     * @param hour            nur bei fester Uhrzeit
     * @param minute          nur bei fester Uhrzeit
     * @param daily           true = täglich, false = wöchentlich am weekday
     * @param weekday         nur bei wöchentlich
     */
    public static long nextTrigger(boolean interval, long intervalMinutes, int hour, int minute,
                                   boolean daily, DayOfWeek weekday, long afterMillis) {
        if (interval) {
            return afterMillis + intervalMinutes * 60_000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime after = Instant.ofEpochMilli(afterMillis).atZone(zone);
        ZonedDateTime candidate = after
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);

        if (daily) {
            if (!candidate.isAfter(after)) {
                candidate = candidate.plusDays(1);
            }

            return candidate.toInstant().toEpochMilli();
        }

        // wöchentlich
        while (candidate.getDayOfWeek() != weekday || !candidate.isAfter(after)) {
            candidate = candidate.plusDays(1);
        }

        return candidate.toInstant().toEpochMilli();
    }

    /** Kurzbeschreibung einer Regel, z.B. "every 1h 30m", "daily at 04:00" oder "every Monday at 04:00". */
    public static String describe(boolean interval, long intervalMinutes, int hour, int minute,
                                  boolean daily, DayOfWeek weekday) {
        if (interval) {
            long hours = intervalMinutes / 60;
            long minutes = intervalMinutes % 60;

            if (hours == 0) {
                return "every " + minutes + "m";
            }

            if (minutes == 0) {
                return "every " + hours + "h";
            }

            return "every " + hours + "h " + minutes + "m";
        }

        String time = String.format("%02d:%02d", hour, minute);

        if (daily) {
            return "daily at " + time;
        }

        return "every " + capitalize(weekday.name()) + " at " + time;
    }

    /** Zeitpunkt für die Anzeige, z.B. "Mon 04:00". */
    public static String formatNextTrigger(long millis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(NEXT_TRIGGER_FORMAT);
    }

    /** "MONDAY" wird zu "Monday". */
    public static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
