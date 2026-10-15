package crqzycat.maintena.gui;

import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.announcement.AnnouncementSchedule;
import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.restart.RestartManager;
import crqzycat.maintena.restart.RestartSchedule;
import net.minecraft.server.dialog.Dialog;
import java.util.Collection;
import java.util.List;

/**
 * Dialog-Seiten für Wartungsmodus (inkl. Whitelist), Restarts und Announcements.
 */
final class ServerPages {

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private static final List<String> DISPLAYS = List.of("chat", "screen");

    private ServerPages() {
    }

    // ==================== Maintenance ====================

    static Dialog maintenance() {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        int count = manager.getWhitelistedPlayers().size();

        return new DialogBuilder("§6Maintenance", Page.MAINTENANCE, null)
                .text(manager.getStatus())
                .textInput("minutes", "Duration in minutes (0 = manual stop)", "0", 6)
                .button("Enable maintenance", "maintenance on $(minutes)")
                .button("Disable maintenance", "maintenance off")
                .open("Whitelisted players (" + count + ")", Page.MAINTENANCE_WHITELIST)
                .button("Show status", "maintenance status")
                .back(Page.MAIN)
                .build();
    }

    static Dialog whitelist() {
        Collection<String> players = MaintenanceManager.getInstance().getWhitelistedPlayers();

        DialogBuilder b = new DialogBuilder("§6Whitelisted players", Page.MAINTENANCE_WHITELIST, null)
                .text(players.isEmpty()
                        ? "§7No players are whitelisted."
                        : "§7Click a player to manage them.")
                .textInput("addplayer", "Add player", "", 16);

        for (String player : players) {
            b.open(player, Page.MAINTENANCE_PLAYER, player);
        }

        return b.button("Add to whitelist", "maintenance add $(addplayer)")
                .buttonIf(!players.isEmpty(), "Clear whitelist", "maintenance clear")
                .back(Page.MAINTENANCE)
                .build();
    }

    static Dialog whitelistPlayer(String name) {
        return new DialogBuilder("§6" + name, Page.MAINTENANCE_PLAYER, name)
                .text("§7This player can join during maintenance.")
                .buttonThen("Remove from whitelist", "maintenance remove " + name,
                        Page.MAINTENANCE_WHITELIST, null)
                .back(Page.MAINTENANCE_WHITELIST)
                .build();
    }

    // ==================== Restart ====================

    static Dialog restart() {
        RestartManager manager = RestartManager.getInstance();
        int count = manager.getSchedules().size();

        return new DialogBuilder("§6Restart", Page.RESTART, null)
                .text(manager.isCountdownActive()
                        ? "§eA restart countdown is currently running."
                        : "§7No restart pending.")
                .textInput("minutes", "Restart in ... minutes", "5", 5)
                .button("Restart now", "restart")
                .button("Restart in X minutes", "restart in $(minutes)")
                .button("Cancel pending restart", "restart cancel")
                .open("Scheduled restarts (" + count + ")", Page.RESTART_SCHEDULES)
                .back(Page.MAIN)
                .build();
    }

    static Dialog restartSchedules() {
        RestartManager manager = RestartManager.getInstance();
        List<RestartSchedule> schedules = manager.getSchedules();

        DialogBuilder b = new DialogBuilder("§6Scheduled restarts", Page.RESTART_SCHEDULES, null)
                .text(schedules.isEmpty()
                        ? "§7No scheduled restarts."
                        : "§7Click a schedule to manage it.");

        for (RestartSchedule schedule : schedules) {
            b.open(schedule.id, Page.RESTART_SCHEDULE, schedule.id);
        }

        return b.open("Add schedule ...", Page.RESTART_ADD)
                .back(Page.RESTART)
                .build();
    }

    static Dialog restartSchedule(String id) {
        RestartManager manager = RestartManager.getInstance();
        RestartSchedule schedule = manager.findSchedule(id);

        DialogBuilder b = new DialogBuilder("§6Restart: " + id, Page.RESTART_SCHEDULE, id);

        if (schedule == null) {
            return b.text("§cThis schedule no longer exists.")
                    .back(Page.RESTART_SCHEDULES)
                    .build();
        }

        return b.text("§7" + manager.describeSchedule(schedule))
                .buttonThen("Remove schedule", "restart schedule remove " + id,
                        Page.RESTART_SCHEDULES, null)
                .back(Page.RESTART_SCHEDULES)
                .build();
    }

    static Dialog restartAdd() {
        return new DialogBuilder("§6Add scheduled restart", Page.RESTART_ADD, null)
                .text("§7Interval: every X hours/minutes. Daily/weekly: at a fixed time (server time).")
                .textInput("name", "Name (required, unique)", "restart", 32)
                .textInput("hours", "Interval: hours", "6", 4)
                .textInput("minutes", "Interval: minutes", "0", 2)
                .textInput("hour", "Time: hour (0-23)", "4", 2)
                .textInput("minute", "Time: minute (0-59)", "0", 2)
                .dropdown("weekday", "Weekday (weekly only)", WEEKDAYS)
                .buttonThen("Add interval", "restart schedule add interval $(hours) $(minutes) $(name)",
                        Page.RESTART_SCHEDULES, null)
                .buttonThen("Add daily", "restart schedule add time $(hour) $(minute) daily $(name)",
                        Page.RESTART_SCHEDULES, null)
                .buttonThen("Add weekly",
                        "restart schedule add time $(hour) $(minute) weekly $(weekday) $(name)",
                        Page.RESTART_SCHEDULES, null)
                .back(Page.RESTART_SCHEDULES)
                .build();
    }

    // ==================== Announcements ====================

    static Dialog announce() {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        int count = manager.getSchedules().size();

        return new DialogBuilder("§6Announcements", Page.ANNOUNCE, null)
                .text("§7Colors: &a, &c, ... On screen, \" | \" separates title and subtitle.")
                .textInput("message", "Message", "", 256)
                .button("Send in chat", "announce chat $(message)")
                .button("Send on screen", "announce screen $(message)")
                .open("Scheduled announcements (" + count + ")", Page.ANNOUNCE_SCHEDULES)
                .back(Page.MAIN)
                .build();
    }

    static Dialog announceSchedules() {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        List<AnnouncementSchedule> schedules = manager.getSchedules();

        DialogBuilder b = new DialogBuilder("§6Scheduled announcements", Page.ANNOUNCE_SCHEDULES, null)
                .text(schedules.isEmpty()
                        ? "§7No scheduled announcements."
                        : "§7Click a schedule to manage it.");

        for (AnnouncementSchedule schedule : schedules) {
            b.open(schedule.id, Page.ANNOUNCE_SCHEDULE, schedule.id);
        }

        return b.open("Add schedule ...", Page.ANNOUNCE_ADD)
                .back(Page.ANNOUNCE)
                .build();
    }

    static Dialog announceSchedule(String id) {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        AnnouncementSchedule schedule = manager.findSchedule(id);

        DialogBuilder b = new DialogBuilder("§6Announcement: " + id, Page.ANNOUNCE_SCHEDULE, id);

        if (schedule == null) {
            return b.text("§cThis schedule no longer exists.")
                    .back(Page.ANNOUNCE_SCHEDULES)
                    .build();
        }

        return b.text("§7" + manager.describeSchedule(schedule))
                .buttonThen("Remove schedule", "announce schedule remove " + id,
                        Page.ANNOUNCE_SCHEDULES, null)
                .back(Page.ANNOUNCE_SCHEDULES)
                .build();
    }

    static Dialog announceAdd() {
        return new DialogBuilder("§6Add scheduled announcement", Page.ANNOUNCE_ADD, null)
                .text("§7Interval: every X hours/minutes. Daily/weekly: at a fixed time (server time).")
                .dropdown("display", "Display", DISPLAYS)
                .textInput("name", "Name (required, unique)", "announcement", 32)
                .textInput("message", "Message", "", 256)
                .textInput("hours", "Interval: hours", "1", 4)
                .textInput("minutes", "Interval: minutes", "0", 2)
                .textInput("hour", "Time: hour (0-23)", "18", 2)
                .textInput("minute", "Time: minute (0-59)", "0", 2)
                .dropdown("weekday", "Weekday (weekly only)", WEEKDAYS)
                .buttonThen("Add interval",
                        "announce schedule add interval $(hours) $(minutes) $(display) $(name) $(message)",
                        Page.ANNOUNCE_SCHEDULES, null)
                .buttonThen("Add daily",
                        "announce schedule add time $(hour) $(minute) daily $(display) $(name) $(message)",
                        Page.ANNOUNCE_SCHEDULES, null)
                .buttonThen("Add weekly",
                        "announce schedule add time $(hour) $(minute) weekly $(weekday) $(display) $(name) $(message)",
                        Page.ANNOUNCE_SCHEDULES, null)
                .back(Page.ANNOUNCE)
                .build();
    }
}
