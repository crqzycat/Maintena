package crqzycat.maintena.announcement;

import java.time.DayOfWeek;

/**
 * Definition einer einzelnen geplanten Announcement.
 * Wie beim Restart: entweder INTERVAL (alle X Minuten) oder TIME (feste Uhrzeit, täglich oder wöchentlich).
 * Zusätzlich hat jede Announcement einen Text und eine Anzeigeart (Chat oder Bildschirm).
 */
public class AnnouncementSchedule {

    public String id;
    public String message;
    public Display display = Display.CHAT;
    public Type type;

    // Nur relevant für Type.INTERVAL
    public long intervalMinutes = -1;

    // Nur relevant für Type.TIME
    public int hour = -1;
    public int minute = -1;
    public Frequency frequency;
    public DayOfWeek weekday; // nur bei Frequency.WEEKLY gesetzt

    // Persistierter Zeitpunkt (epoch millis) der nächsten Ausführung dieser Regel
    public long nextTrigger = -1;

    public enum Type {
        INTERVAL,
        TIME
    }

    public enum Frequency {
        DAILY,
        WEEKLY
    }

    public enum Display {
        CHAT,
        SCREEN
    }
}
