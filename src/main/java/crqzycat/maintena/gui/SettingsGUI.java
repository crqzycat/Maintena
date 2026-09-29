package crqzycat.maintena.gui;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.ban.IpBanManager;
import crqzycat.maintena.gui.SettingsGUIHandler.Access;
import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import crqzycat.maintena.gui.SettingsGUIHandler.Section;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.restart.RestartManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.players.IpBanListEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Baut alle Dialog-Seiten der Settings-GUI. Jeder Button führt einen ganz normalen Befehl aus
 * (z.B. "maintenance on $(minutes)"), die Werte der Eingabefelder werden über $(key) eingesetzt.
 * Dadurch gelten immer die echten Rechte-Prüfungen der Befehle.
 *
 * Welche Bereiche/Buttons überhaupt vorkommen, entscheidet SettingsGUIHandler.Access.
 */
public final class SettingsGUI {

    private static final int BUTTON_WIDTH = 150;
    private static final int INPUT_WIDTH = 200;
    private static final int TEXT_WIDTH = 300;

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private SettingsGUI() {
    }

    // ==================== Einstieg ====================

    public static Dialog build(Page page, Access a) {
        return switch (page) {
            case MAIN -> main(a);
            case MAINTENANCE -> maintenance(a);
            case RESTART -> restart(a);
            case RESTART_ADD -> restartAdd(a);
            case ANNOUNCE -> announce(a);
            case ANNOUNCE_ADD -> announceAdd(a);
            case BAN -> ban(a);
            case IP_BAN -> ipBan(a);
        };
    }

    public static Dialog noAccess() {
        return notice("§cMaintena Settings", "You don't have permission to use any Maintena commands.");
    }

    // ==================== Seiten ====================

    private static Dialog main(Access a) {
        Builder b = new Builder("§6Maintena Settings");

        b.text(a.isAdmin()
                ? "§7Access level: §aAdmin"
                : "§7Access level: §eLimited §7(only your permitted areas are shown)");

        b.buttonIf(Section.MAINTENANCE.isVisibleFor(a), "Maintenance", "settingsgui maintenance");
        b.buttonIf(Section.RESTART.isVisibleFor(a), "Restart", "settingsgui restart");
        b.buttonIf(Section.ANNOUNCE.isVisibleFor(a), "Announcements", "settingsgui announce");
        b.buttonIf(Section.BAN.isVisibleFor(a), "Bans", "settingsgui ban");
        b.buttonIf(Section.IP_BAN.isVisibleFor(a), "IP bans", "settingsgui ipban");
        b.buttonIf(a.can("maintena", "reload"), "Reload config", "maintena reload");

        return b.build();
    }

    private static Dialog maintenance(Access a) {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        Collection<String> whitelist = manager.getWhitelistedPlayers();
        boolean hasWhitelist = !whitelist.isEmpty();

        return new Builder("§6Maintenance")
                .text(manager.getStatus())
                .textInput("minutes", "Duration in minutes (0 = manual stop)", "0", 6)
                .textInput("addplayer", "Player to add to the whitelist", "", 16)
                .dropdown("whitelisted", "Whitelisted players", whitelist)
                .button("Enable maintenance", "maintenance on $(minutes)")
                .button("Disable maintenance", "maintenance off")
                .button("Add to whitelist", "maintenance add $(addplayer)")
                .buttonIf(hasWhitelist, "Remove selected", "maintenance remove $(whitelisted)")
                .buttonIf(hasWhitelist, "Clear whitelist", "maintenance clear")
                .button("Show status", "maintenance status")
                .back()
                .build();
    }

    private static Dialog restart(Access a) {
        RestartManager manager = RestartManager.getInstance();

        List<String> scheduleIds = manager.getSchedules().stream().map(s -> s.id).toList();

        return new Builder("§6Restart")
                .text(manager.isCountdownActive()
                        ? "§eA restart countdown is currently running."
                        : "§7No restart pending.")
                .text(manager.getScheduleListText())
                .textInput("minutes", "Restart in ... minutes", "5", 5)
                .dropdown("schedule", "Scheduled restarts", scheduleIds)
                .button("Restart now", "restart")
                .button("Restart in X minutes", "restart in $(minutes)")
                .button("Cancel pending restart", "restart cancel")
                .button("Add schedule ...", "settingsgui restart_add")
                .buttonIf(!scheduleIds.isEmpty(), "Remove selected schedule", "restart schedule remove $(schedule)")
                .button("List schedules", "restart schedule list")
                .back()
                .build();
    }

    private static Dialog restartAdd(Access a) {
        return new Builder("§6Add scheduled restart")
                .text("§7Interval: every X hours/minutes. Daily/weekly: at a fixed time (server time).")
                .textInput("name", "Name (required, unique)", "restart", 32)
                .textInput("hours", "Interval: hours", "6", 4)
                .textInput("minutes", "Interval: minutes", "0", 2)
                .textInput("hour", "Time: hour (0-23)", "4", 2)
                .textInput("minute", "Time: minute (0-59)", "0", 2)
                .dropdown("weekday", "Weekday (weekly only)", WEEKDAYS)
                .button("Add interval", "restart schedule add interval $(hours) $(minutes) $(name)")
                .button("Add daily", "restart schedule add time $(hour) $(minute) daily $(name)")
                .button("Add weekly", "restart schedule add time $(hour) $(minute) weekly $(weekday) $(name)")
                .backTo("settingsgui restart")
                .build();
    }

    private static Dialog announce(Access a) {
        AnnouncementManager manager = AnnouncementManager.getInstance();

        List<String> scheduleIds = manager.getSchedules().stream().map(s -> s.id).toList();

        return new Builder("§6Announcements")
                .text(manager.getScheduleListText())
                .text("§7Colors: &a, &c, ... On screen, \" | \" separates title and subtitle.")
                .checkbox("display", "Show on screen (otherwise in chat)", false, "screen", "chat")
                .textInput("message", "Message", "", 256)
                .dropdown("schedule", "Scheduled announcements", scheduleIds)
                .button("Send now", "announce $(display) $(message)")
                .button("Add schedule ...", "settingsgui announce_add")
                .buttonIf(!scheduleIds.isEmpty(), "Remove selected schedule", "announce schedule remove $(schedule)")
                .button("List schedules", "announce schedule list")
                .back()
                .build();
    }

    private static Dialog announceAdd(Access a) {
        return new Builder("§6Add scheduled announcement")
                .text("§7Interval: every X hours/minutes. Daily/weekly: at a fixed time (server time).")
                .checkbox("display", "Show on screen (otherwise in chat)", false, "screen", "chat")
                .textInput("name", "Name (required, unique)", "announcement", 32)
                .textInput("message", "Message", "", 256)
                .textInput("hours", "Interval: hours", "1", 4)
                .textInput("minutes", "Interval: minutes", "0", 2)
                .textInput("hour", "Time: hour (0-23)", "18", 2)
                .textInput("minute", "Time: minute (0-59)", "0", 2)
                .dropdown("weekday", "Weekday (weekly only)", WEEKDAYS)
                .button("Add interval",
                        "announce schedule add interval $(hours) $(minutes) $(display) $(name) $(message)")
                .button("Add daily",
                        "announce schedule add time $(hour) $(minute) daily $(display) $(name) $(message)")
                .button("Add weekly",
                        "announce schedule add time $(hour) $(minute) weekly $(weekday) $(display) $(name) $(message)")
                .backTo("settingsgui announce")
                .build();
    }

    private static Dialog ban(Access a) {
        List<String> banned = BanManager.getInstance().getActiveBans(a.server())
                .stream()
                .map(entry -> entry.getUser().name())
                .toList();

        return new Builder("§6Bans")
                .text("§7Duration examples: 30m, 24h, 7d, 1d12h")
                .textInput("player", "Player name", "", 16)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .dropdown("banned", "Banned players", banned)
                .buttonIf(a.can("ban"), "Ban permanently", "ban $(player) $(reason)")
                .buttonIf(a.can("ban"), "Ban temporarily", "ban $(player) $(duration) $(reason)")
                .buttonIf(a.can("ban") && !banned.isEmpty(), "Ban info", "ban info $(banned)")
                .buttonIf(a.can("unban") && !banned.isEmpty(), "Unban selected", "unban $(banned)")
                .buttonIf(a.can("banlist"), "Show ban list", "banlist")
                .back()
                .build();
    }

    private static Dialog ipBan(Access a) {
        List<String> bannedIps = IpBanManager.getInstance().getActiveBans(a.server())
                .stream()
                .map(IpBanListEntry::getUser)
                .toList();

        Collection<String> online = a.source().getOnlinePlayerNames();

        return new Builder("§6IP bans")
                .text("§7Target: online player name or IP address. Duration examples: 30m, 24h, 7d")
                .textInput("target", "Player or IP", "", 45)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .dropdown("online", "Online players", online)
                .dropdown("bannedip", "Banned IPs", bannedIps)
                .buttonIf(a.can("ip"), "Show IP of player", "ip $(online)")
                .buttonIf(a.can("ipban"), "IP ban permanently", "ipban $(target) $(reason)")
                .buttonIf(a.can("ipban"), "IP ban temporarily", "ipban $(target) $(duration) $(reason)")
                .buttonIf(a.can("ipban") && !bannedIps.isEmpty(), "IP ban info", "ipban info $(bannedip)")
                .buttonIf(a.can("ipunban") && !bannedIps.isEmpty(), "Remove IP ban", "ipunban $(bannedip)")
                .buttonIf(a.can("ipbanlist"), "Show IP ban list", "ipbanlist")
                .back()
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

    private static ActionButton actionButton(String label, String command) {
        ParsedTemplate template = ParsedTemplate.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive(command))
                .result()
                .orElseThrow(() -> new IllegalStateException("Invalid command template: " + command));

        return new ActionButton(
                new CommonButtonData(Component.literal(label), BUTTON_WIDTH),
                Optional.<Action>of(new CommandTemplate(template))
        );
    }

    /** Kleiner Baukasten für Dialoge mit Text, Eingabefeldern und Buttons. */
    private static final class Builder {

        private final Component title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<Input> inputs = new ArrayList<>();
        private final List<ActionButton> buttons = new ArrayList<>();
        private ActionButton exit;

        Builder(String title) {
            this.title = Component.literal(title);
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

        Builder checkbox(String key, String label, boolean initial, String onTrue, String onFalse) {
            inputs.add(new Input(key, new BooleanInput(Component.literal(label), initial, onTrue, onFalse)));
            return this;
        }

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

        Builder button(String label, String command) {
            buttons.add(actionButton(label, command));
            return this;
        }

        Builder buttonIf(boolean condition, String label, String command) {
            return condition ? button(label, command) : this;
        }

        Builder back() {
            return backTo("settingsgui");
        }

        Builder backTo(String command) {
            exit = actionButton("« Back", command);
            return this;
        }

        Dialog build() {
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