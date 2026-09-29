package crqzycat.maintena.gui;

import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.announcement.AnnouncementSchedule;
import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.ban.IpBanManager;
import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.restart.RestartManager;
import crqzycat.maintena.restart.RestartSchedule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.UserBanListEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Baut alle Dialog-Seiten der Settings-GUI (nur für Admins, geöffnet über /maintena).
 *
 * Aufbau: Übersichtsseiten haben Buttons für Aktionen und für Listen. Eine Liste (z.B. die
 * Whitelist) ist eine eigene Seite mit einem Button pro Eintrag; ein Klick darauf öffnet die
 * Verwaltungsseite des Eintrags (z.B. "Remove from whitelist"). Nach einer Aktion geht es
 * automatisch auf die passende Seite zurück, damit man nicht neu öffnen muss.
 *
 * Alle Buttons senden eine custom click action an den Server (siehe SettingsGUIHandler), die
 * Werte der Eingabefelder werden dort über $(key) in den Befehl eingesetzt.
 */
public final class SettingsGUI {

    private static final int BUTTON_WIDTH = 150;
    private static final int INPUT_WIDTH = 200;
    private static final int TEXT_WIDTH = 300;

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private static final List<String> DISPLAYS = List.of("chat", "screen");

    private SettingsGUI() {
    }

    // ==================== Einstieg ====================

    public static Dialog build(Page page, CommandSourceStack a, String arg) {
        return switch (page) {
            case MAIN -> main();
            case MAINTENANCE -> maintenance();
            case MAINTENANCE_WHITELIST -> whitelist();
            case MAINTENANCE_PLAYER -> whitelistPlayer(arg);
            case RESTART -> restart();
            case RESTART_SCHEDULES -> restartSchedules();
            case RESTART_SCHEDULE -> restartSchedule(arg);
            case RESTART_ADD -> restartAdd();
            case ANNOUNCE -> announce();
            case ANNOUNCE_SCHEDULES -> announceSchedules();
            case ANNOUNCE_SCHEDULE -> announceSchedule(arg);
            case ANNOUNCE_ADD -> announceAdd();
            case BAN -> ban(a);
            case BAN_LIST -> banList(a);
            case BAN_ENTRY -> banEntry(a, arg);
            case IP_BAN -> ipBan(a);
            case IPBAN_LIST -> ipBanList(a);
            case IPBAN_ENTRY -> ipBanEntry(a, arg);
            case IP_ONLINE -> ipOnline(a);
            case IP_PLAYER -> ipPlayer(arg);
        };
    }

    public static Dialog noAccess() {
        return notice("§cMaintena Settings", "You don't have permission to use any Maintena commands.");
    }

    // ==================== Hauptmenü ====================

    private static Dialog main() {
        return new Builder("§6Maintena Settings", Page.MAIN, null)
                .text("§7Choose an area to configure.")
                .open("Maintenance", Page.MAINTENANCE)
                .open("Restart", Page.RESTART)
                .open("Announcements", Page.ANNOUNCE)
                .open("Bans", Page.BAN)
                .open("IP bans", Page.IP_BAN)
                .button("Reload config", "maintena reload")
                .build();
    }

    // ==================== Maintenance ====================

    private static Dialog maintenance() {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        int count = manager.getWhitelistedPlayers().size();

        return new Builder("§6Maintenance", Page.MAINTENANCE, null)
                .text(manager.getStatus())
                .textInput("minutes", "Duration in minutes (0 = manual stop)", "0", 6)
                .button("Enable maintenance", "maintenance on $(minutes)")
                .button("Disable maintenance", "maintenance off")
                .open("Whitelisted players (" + count + ")", Page.MAINTENANCE_WHITELIST)
                .button("Show status", "maintenance status")
                .back(Page.MAIN)
                .build();
    }

    private static Dialog whitelist() {
        Collection<String> players = MaintenanceManager.getInstance().getWhitelistedPlayers();

        Builder b = new Builder("§6Whitelisted players", Page.MAINTENANCE_WHITELIST, null)
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

    private static Dialog whitelistPlayer(String name) {
        return new Builder("§6" + name, Page.MAINTENANCE_PLAYER, name)
                .text("§7This player can join during maintenance.")
                .buttonThen("Remove from whitelist", "maintenance remove " + name,
                        Page.MAINTENANCE_WHITELIST, null)
                .back(Page.MAINTENANCE_WHITELIST)
                .build();
    }

    // ==================== Restart ====================

    private static Dialog restart() {
        RestartManager manager = RestartManager.getInstance();
        int count = manager.getSchedules().size();

        return new Builder("§6Restart", Page.RESTART, null)
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

    private static Dialog restartSchedules() {
        RestartManager manager = RestartManager.getInstance();
        List<RestartSchedule> schedules = manager.getSchedules();

        Builder b = new Builder("§6Scheduled restarts", Page.RESTART_SCHEDULES, null)
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

    private static Dialog restartSchedule(String id) {
        RestartManager manager = RestartManager.getInstance();
        RestartSchedule schedule = manager.findSchedule(id);

        Builder b = new Builder("§6Restart: " + id, Page.RESTART_SCHEDULE, id);

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

    private static Dialog restartAdd() {
        return new Builder("§6Add scheduled restart", Page.RESTART_ADD, null)
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

    private static Dialog announce() {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        int count = manager.getSchedules().size();

        return new Builder("§6Announcements", Page.ANNOUNCE, null)
                .text("§7Colors: &a, &c, ... On screen, \" | \" separates title and subtitle.")
                .textInput("message", "Message", "", 256)
                .button("Send in chat", "announce chat $(message)")
                .button("Send on screen", "announce screen $(message)")
                .open("Scheduled announcements (" + count + ")", Page.ANNOUNCE_SCHEDULES)
                .back(Page.MAIN)
                .build();
    }

    private static Dialog announceSchedules() {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        List<AnnouncementSchedule> schedules = manager.getSchedules();

        Builder b = new Builder("§6Scheduled announcements", Page.ANNOUNCE_SCHEDULES, null)
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

    private static Dialog announceSchedule(String id) {
        AnnouncementManager manager = AnnouncementManager.getInstance();
        AnnouncementSchedule schedule = manager.findSchedule(id);

        Builder b = new Builder("§6Announcement: " + id, Page.ANNOUNCE_SCHEDULE, id);

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

    private static Dialog announceAdd() {
        return new Builder("§6Add scheduled announcement", Page.ANNOUNCE_ADD, null)
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

    // ==================== Bans ====================

    private static Dialog ban(CommandSourceStack a) {
        int count = BanManager.getInstance().getActiveBans(a.getServer()).size();

        return new Builder("§6Bans", Page.BAN, null)
                .text("§7Duration examples: 30m, 24h, 7d, 1d12h")
                .textInput("player", "Player name", "", 16)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Ban permanently", "ban $(player) $(reason)")
                .button("Ban temporarily", "ban $(player) $(duration) $(reason)")
                .open("Banned players (" + count + ")", Page.BAN_LIST)
                .button("Show ban list", "banlist")
                .back(Page.MAIN)
                .build();
    }

    private static Dialog banList(CommandSourceStack a) {
        List<String> banned = BanManager.getInstance().getActiveBans(a.getServer())
                .stream()
                .map(entry -> entry.getUser().name())
                .toList();

        Builder b = new Builder("§6Banned players", Page.BAN_LIST, null)
                .text(banned.isEmpty() ? "§7No players are banned." : "§7Click a player to manage them.");

        for (String name : banned) {
            b.open(name, Page.BAN_ENTRY, name);
        }

        return b.back(Page.BAN).build();
    }

    private static Dialog banEntry(CommandSourceStack a, String name) {
        BanManager manager = BanManager.getInstance();
        UserBanListEntry entry = manager.findBan(a.getServer(), name);

        Builder b = new Builder("§6" + name, Page.BAN_ENTRY, name);

        if (entry == null) {
            return b.text("§cThis player is not banned (anymore).")
                    .back(Page.BAN_LIST)
                    .build();
        }

        b.text("§7" + manager.describeRemaining(entry));

        if (entry.getReason() != null && !entry.getReason().isBlank()) {
            b.text("§7Reason: §f" + entry.getReason());
        }

        return b.buttonThen("Unban", "unban " + name, Page.BAN_LIST, null)
                .button("Ban info (chat)", "ban info " + name)
                .back(Page.BAN_LIST)
                .build();
    }

    // ==================== IP bans ====================

    private static Dialog ipBan(CommandSourceStack a) {
        int count = IpBanManager.getInstance().getActiveBans(a.getServer()).size();

        return new Builder("§6IP bans", Page.IP_BAN, null)
                .text("§7Target: player name or IP address. Duration examples: 30m, 24h, 7d")
                .textInput("target", "Player or IP", "", 45)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("IP ban permanently", "ipban $(target) $(reason)")
                .button("IP ban temporarily", "ipban $(target) $(duration) $(reason)")
                .open("Banned IPs (" + count + ")", Page.IPBAN_LIST)
                .open("Online players", Page.IP_ONLINE)
                .button("Show IP ban list", "ipbanlist")
                .back(Page.MAIN)
                .build();
    }

    private static Dialog ipBanList(CommandSourceStack a) {
        List<String> ips = IpBanManager.getInstance().getActiveBans(a.getServer())
                .stream()
                .map(IpBanListEntry::getUser)
                .toList();

        Builder b = new Builder("§6Banned IPs", Page.IPBAN_LIST, null)
                .text(ips.isEmpty() ? "§7No IPs are banned." : "§7Click an IP to manage it.");

        for (String ip : ips) {
            b.open(ip, Page.IPBAN_ENTRY, ip);
        }

        return b.back(Page.IP_BAN).build();
    }

    private static Dialog ipBanEntry(CommandSourceStack a, String ip) {
        IpBanManager manager = IpBanManager.getInstance();
        IpBanListEntry entry = manager.findBan(a.getServer(), ip);

        Builder b = new Builder("§6" + ip, Page.IPBAN_ENTRY, ip);

        if (entry == null) {
            return b.text("§cThis IP is not banned (anymore).")
                    .back(Page.IPBAN_LIST)
                    .build();
        }

        b.text("§7" + manager.describeRemaining(entry));

        if (entry.getReason() != null && !entry.getReason().isBlank()) {
            b.text("§7Reason: §f" + entry.getReason());
        }

        return b.buttonThen("Remove IP ban", "ipunban " + ip, Page.IPBAN_LIST, null)
                .button("IP ban info (chat)", "ipban info " + ip)
                .back(Page.IPBAN_LIST)
                .build();
    }

    private static Dialog ipOnline(CommandSourceStack a) {
        Collection<String> online = a.getOnlinePlayerNames();

        Builder b = new Builder("§6Online players", Page.IP_ONLINE, null)
                .text(online.isEmpty() ? "§7Nobody is online." : "§7Click a player to manage their IP.");

        for (String name : online) {
            b.open(name, Page.IP_PLAYER, name);
        }

        return b.back(Page.IP_BAN).build();
    }

    private static Dialog ipPlayer(String name) {
        return new Builder("§6" + name, Page.IP_PLAYER, name)
                .text("§7Duration examples: 30m, 24h, 7d")
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Show IP (chat)", "ip " + name)
                .button("IP ban permanently", "ipban " + name + " $(reason)")
                .button("IP ban temporarily", "ipban " + name + " $(duration) $(reason)")
                .back(Page.IP_ONLINE)
                .build();
    }

    // ==================== Helfer ====================

    private static Dialog notice(String title, String message) {
        CommonDialogData common = new CommonDialogData(
                Component.literal(title),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(message), PlainMessage.DEFAULT_WIDTH)),
                List.of()
        );

        return new NoticeDialog(common, NoticeDialog.DEFAULT_ACTION);
    }

    /**
     * Ein Button sendet eine custom click action an den Server (statt einen Befehl auf dem Client
     * auszuführen, was Minecraft mit einer Bestätigungs-Warnung quittieren würde).
     *
     * @param command Befehl mit $(key)-Platzhaltern, oder null (reine Navigation)
     * @param page    Seite, die danach geöffnet wird
     * @param arg     Parameter dieser Seite (z.B. Spielername), oder null
     */
    private static ActionButton actionButton(String label, String command, Page page, String arg) {
        CompoundTag additions = new CompoundTag();

        if (command != null) {
            additions.putString(SettingsGUIHandler.KEY_COMMAND, command);
        }

        additions.putString(SettingsGUIHandler.KEY_PAGE, page.name());

        if (arg != null) {
            additions.putString(SettingsGUIHandler.KEY_ARG, arg);
        }

        Action action = new CustomAll(
                Identifier.fromNamespaceAndPath("maintena", SettingsGUIHandler.ACTION_PATH),
                Optional.of(additions)
        );

        return new ActionButton(
                new CommonButtonData(Component.literal(label), BUTTON_WIDTH),
                Optional.of(action)
        );
    }

    /** Kleiner Baukasten für Dialoge mit Text, Eingabefeldern und Buttons. */
    private static final class Builder {

        private final Component title;
        private final Page page;
        private final String arg;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<Input> inputs = new ArrayList<>();
        private final List<ActionButton> buttons = new ArrayList<>();
        private ActionButton exit;

        Builder(String title, Page page, String arg) {
            this.title = Component.literal(title);
            this.page = page;
            this.arg = arg;
        }

        Builder text(String message) {
            if (message != null && !message.isBlank()) {
                body.add(new PlainMessage(Component.literal(message), TEXT_WIDTH));
            }
            return this;
        }

        Builder textInput(String key, String label, String initial, int maxLength) {
            inputs.add(new Input(key, new TextInput(
                    INPUT_WIDTH, Component.literal(label), true, initial, maxLength, Optional.empty()
            )));
            return this;
        }

        /** Auswahlfeld für Formulare (z.B. Wochentag). Listen von Einträgen sind stattdessen Buttons. */
        Builder dropdown(String key, String label, Collection<String> values) {
            if (values.isEmpty()) {
                return this; // leere Auswahl wird vom Client nicht akzeptiert
            }

            List<SingleOptionInput.Entry> entries = new ArrayList<>();
            boolean first = true;

            for (String value : values) {
                entries.add(new SingleOptionInput.Entry(value, Optional.empty(), first));
                first = false;
            }

            inputs.add(new Input(key, new SingleOptionInput(
                    INPUT_WIDTH, entries, Component.literal(label), true
            )));
            return this;
        }

        /** Führt den Befehl aus und bleibt danach auf dieser Seite (Anzeige wird aktualisiert). */
        Builder button(String label, String command) {
            buttons.add(actionButton(label, command, page, arg));
            return this;
        }

        Builder buttonIf(boolean condition, String label, String command) {
            return condition ? button(label, command) : this;
        }

        /** Führt den Befehl aus und öffnet danach eine andere Seite. */
        Builder buttonThen(String label, String command, Page next, String nextArg) {
            buttons.add(actionButton(label, command, next, nextArg));
            return this;
        }

        /** Öffnet nur eine andere Seite. */
        Builder open(String label, Page next) {
            return open(label, next, null);
        }

        Builder open(String label, Page next, String nextArg) {
            buttons.add(actionButton(label, null, next, nextArg));
            return this;
        }

        Builder back(Page previous) {
            exit = actionButton("« Back", null, previous, null);
            return this;
        }

        Dialog build() {
            // Ein Dialog braucht mindestens einen Button: gibt es nur "Back", wird er normaler Button
            if (buttons.isEmpty() && exit != null) {
                buttons.add(exit);
                exit = null;
            }

            if (buttons.isEmpty()) {
                return noAccess();
            }

            CommonDialogData common = new CommonDialogData(
                    title,
                    Optional.empty(),
                    true,
                    false,
                    DialogAction.CLOSE,
                    List.copyOf(body),
                    List.copyOf(inputs)
            );

            return new MultiActionDialog(common, List.copyOf(buttons), Optional.ofNullable(exit), 2);
        }
    }
}
