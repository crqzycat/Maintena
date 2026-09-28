package crqzycat.maintena.announcement;

import java.util.ArrayList;
import java.util.List;

public class AnnouncementData {

    public List<AnnouncementSchedule> schedules = new ArrayList<>();

    public static class Config {
        // Format für Announcements im Chat, %message% wird durch den Text ersetzt
        public String chatFormat = "§6[§eAnnouncement§6] §f%message%";

        // Anzeigedauer für Announcements auf dem Bildschirm (Title), in Ticks (20 Ticks = 1 Sekunde)
        public int screenFadeInTicks = 10;
        public int screenStayTicks = 70;
        public int screenFadeOutTicks = 20;

        public String scheduleListHeader = "§6=== Scheduled Announcements ===";
        public String scheduleListEmpty = "§eNo scheduled announcements configured.";
    }
}
