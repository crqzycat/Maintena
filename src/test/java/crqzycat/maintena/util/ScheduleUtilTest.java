package crqzycat.maintena.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleUtilTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** Zeitpunkt in der Zeitzone des Servers, so wie ScheduleUtil ihn auch benutzt. */
    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli();
    }

    // 2026-10-15 ist ein Donnerstag

    @Test
    void intervalAddsMinutes() {
        long after = at(2026, 10, 15, 10, 0);

        assertEquals(after + 90 * 60_000L,
                ScheduleUtil.nextTrigger(true, 90, -1, -1, false, null, after));
    }

    @Test
    void dailyLaterTodayWhenTimeIsStillAhead() {
        long after = at(2026, 10, 15, 3, 0);

        assertEquals(at(2026, 10, 15, 4, 0),
                ScheduleUtil.nextTrigger(false, -1, 4, 0, true, null, after));
    }

    @Test
    void dailyTomorrowWhenTimeHasPassed() {
        long after = at(2026, 10, 15, 5, 0);

        assertEquals(at(2026, 10, 16, 4, 0),
                ScheduleUtil.nextTrigger(false, -1, 4, 0, true, null, after));
    }

    @Test
    void dailyTomorrowWhenExactlyOnTheMinute() {
        long after = at(2026, 10, 15, 4, 0);

        assertEquals(at(2026, 10, 16, 4, 0),
                ScheduleUtil.nextTrigger(false, -1, 4, 0, true, null, after));
    }

    @Test
    void weeklyFindsNextWeekday() {
        long after = at(2026, 10, 15, 10, 0); // Donnerstag

        assertEquals(at(2026, 10, 19, 4, 0), // Montag
                ScheduleUtil.nextTrigger(false, -1, 4, 0, false, DayOfWeek.MONDAY, after));
    }

    @Test
    void weeklySameDayWhenTimeIsStillAhead() {
        long after = at(2026, 10, 19, 3, 0); // Montag früh

        assertEquals(at(2026, 10, 19, 4, 0),
                ScheduleUtil.nextTrigger(false, -1, 4, 0, false, DayOfWeek.MONDAY, after));
    }

    @Test
    void weeklyNextWeekWhenTimeHasPassed() {
        long after = at(2026, 10, 19, 5, 0); // Montag nach der Uhrzeit

        assertEquals(at(2026, 10, 26, 4, 0),
                ScheduleUtil.nextTrigger(false, -1, 4, 0, false, DayOfWeek.MONDAY, after));
    }

    @Test
    void describesIntervals() {
        assertEquals("every 45m", ScheduleUtil.describe(true, 45, -1, -1, false, null));
        assertEquals("every 2h", ScheduleUtil.describe(true, 120, -1, -1, false, null));
        assertEquals("every 1h 30m", ScheduleUtil.describe(true, 90, -1, -1, false, null));
    }

    @Test
    void describesFixedTimes() {
        assertEquals("daily at 04:05", ScheduleUtil.describe(false, -1, 4, 5, true, null));
        assertEquals("every Monday at 04:00", ScheduleUtil.describe(false, -1, 4, 0, false, DayOfWeek.MONDAY));
    }

    @Test
    void capitalizes() {
        assertEquals("Monday", ScheduleUtil.capitalize("MONDAY"));
        assertEquals("Monday", ScheduleUtil.capitalize("monday"));
        assertEquals("", ScheduleUtil.capitalize(""));
        assertEquals(null, ScheduleUtil.capitalize(null));
    }
}
