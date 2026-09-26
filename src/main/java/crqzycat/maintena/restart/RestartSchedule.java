package crqzycat.maintena.restart;

import java.time.DayOfWeek;

/**
 * Definition eines einzelnen geplanten Restarts.
 * Entweder INTERVAL (alle X Minuten) oder TIME (feste Uhrzeit, täglich oder wöchentlich).
 */
public class RestartSchedule {

    public String id;
    public Type type;

    // Nur relevant für Type.INTERVAL
    public long intervalMinutes = -1;

    // Nur relevant für Type.TIME
    public int hour = -1;
    public int minute = -1;
    public Frequency frequency;
    public DayOfWeek weekday; // nur bei Frequency.WEEKLY gesetzt

    // Persistierter Zeitpunkt (epoch millis) des nächsten Restarts dieser Regel
    public long nextTrigger = -1;

    public enum Type {
        INTERVAL,
        TIME
    }

    public enum Frequency {
        DAILY,
        WEEKLY
    }
}
