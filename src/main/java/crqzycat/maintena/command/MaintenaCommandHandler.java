package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.freeze.FreezeManager;
import crqzycat.maintena.gui.SettingsGUIHandler;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.mute.MuteManager;
import crqzycat.maintena.restart.RestartManager;
import crqzycat.maintena.restart.RestartSchedule;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Globaler /maintena Befehl: Reload für Maintenance + Restart.
 * Hängt zusätzlich den kompletten "restart"-Baum unter /maintena restart
 * UND als eigenständigen /restart Befehl ein, sowie den kompletten
 * "maintenance"-Baum (aus MaintenanceCommandHandler) unter /maintena maintenance
 * (der eigenständige /maintenance Befehl bleibt davon unberührt bestehen).
 * Ebenso der "announce"-Baum (AnnouncementCommandHandler) unter /maintena announce und als /announce.
 * Ebenso die Ban-Befehle (BanCommandHandler): /ban, /unban, /banlist, sowie die IP-Ban-Befehle
 * (IpBanCommandHandler): /ip, /ipban, /ipunban, /ipbanlist. Alle auch unter /maintena.
 * Der bloße Aufruf von /restart (ohne Subcommand) löst direkt einen sofortigen Restart aus
 * (ehemals /restart now).
 * <p>
 * Die Settings-GUI (nur für Admins) öffnet sich über /maintena:
 * /maintena               Hauptmenü
 * /maintena maintenance   Maintenance-Menü
 * /maintena restart       Restart-Menü (der sofortige Restart ist dort ein Button bzw. /restart)
 * /maintena announce      Announcement-Menü
 * /maintena ban           Ban-Menü
 * /maintena ipban         IP-Ban-Menü
 * /maintena mute          Mute-Menü (Chat-Timeouts)
 * /maintena vanish        Vanish-Menü
 * /maintena disguise      Disguise-Menü (auch /maintena undisguise)
 * /maintena freeze        Freeze-Menü (auch /maintena unfreeze)
 * /maintena nick          Nick-Menü
 */
public class MaintenaCommandHandler {

    private static final List<String> WEEKDAYS = List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private static final List<Integer> INTERVAL_HOURS_SUGGESTIONS = List.of(0, 1, 2, 3, 6, 12, 24);

    private static final List<Integer> INTERVAL_MINUTES_SUGGESTIONS = List.of(0, 15, 30, 45);

    private static final List<Integer> MANUAL_MINUTES_SUGGESTIONS = List.of(1, 5, 10, 15, 30, 60);

    private static final List<Integer> HOUR_SUGGESTIONS = List.of(0, 6, 12, 18, 23);

    private static final List<Integer> MINUTE_SUGGESTIONS = List.of(0, 15, 30, 45);

    /**
     * Zahlen-Vorschläge, die numerisch statt alphabetisch sortiert werden
     * (sonst kommt "1440" vor "15" und "180").
     */
    private static SuggestionProvider<CommandSourceStack> intSuggestions(List<Integer> values) {
        return (context, builder) -> {
            String typed = builder.getRemaining();

            for (int value : values) {
                if (String.valueOf(value).startsWith(typed)) {
                    builder.suggest(value);
                }
            }

            return builder.buildFuture();
        };
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerMaintenaCommand(dispatcher));
    }

    private static void registerMaintenaCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Vanilla /ban und /banlist durch die Maintena-Versionen ersetzen
        BanCommandHandler.removeVanillaCommands(dispatcher);

        dispatcher.register(Commands.literal("maintena").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                // /maintena ohne Argumente -> Hauptmenü
                .executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.MAIN))

                .then(Commands.literal("reload").executes(ctx -> {
                    MaintenanceManager.getInstance().reload();
                    RestartManager.getInstance().reload();
                    AnnouncementManager.getInstance().reload();
                    BanManager.getInstance().reload();
                    MuteManager.getInstance().reload();
                    FreezeManager.getInstance().reload();

                    ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Maintena config reloaded"), true);

                    return 1;
                }))

                // /maintena restart ... (wie /restart, nur dass ohne Argumente das Menü aufgeht)
                .then(buildRestartTree(true))

                // /maintena announce ... (identischer Baum wie das eigenständige /announce)
                .then(AnnouncementCommandHandler.buildAnnounceTree(true))

                // /maintena ban|unban|banlist ... (identisch zu den eigenständigen Befehlen)
                .then(BanCommandHandler.buildBanTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.BAN))).then(BanCommandHandler.buildUnbanTree()).then(BanCommandHandler.buildBanlistTree()).then(IpBanCommandHandler.buildIpTree()).then(IpBanCommandHandler.buildIpBanTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.IP_BAN))).then(IpBanCommandHandler.buildIpUnbanTree()).then(IpBanCommandHandler.buildIpBanlistTree())

                // /maintena mute|unmute|mutelist ... (identisch zu den eigenständigen Befehlen)
                .then(MuteCommandHandler.buildMuteTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.MUTE))).then(MuteCommandHandler.buildUnmuteTree()).then(MuteCommandHandler.buildMutelistTree())

                // /maintena vanish ... (identisch zu /vanish, ohne Argumente öffnet sich das Menü)
                .then(VanishCommandHandler.buildVanishTree("vanish").executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.VANISH)))

                // /maintena disguise|undisguise ... (identisch zu den eigenständigen Befehlen, ohne Argumente öffnet sich das Menü)
                .then(DisguiseCommandHandler.buildDisguiseTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.DISGUISE))).then(DisguiseCommandHandler.buildUndisguiseTree())

                // /maintena freeze|unfreeze ... (identisch zu den eigenständigen Befehlen, ohne Argumente öffnet sich das Menü)
                .then(FreezeCommandHandler.buildFreezeTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.FREEZE))).then(FreezeCommandHandler.buildUnfreezeTree())

                // /maintena nick ... (identisch zum eigenständigen /nick, ohne Argumente öffnet sich das Menü)
                .then(NickCommandHandler.buildNickTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.NICK)))

                // /maintena maintenance ... (identischer Baum wie das eigenständige /maintenance)
                .then(MaintenanceCommandHandler.buildMaintenanceCommand().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.MAINTENANCE))));

        // Eigenständiger /restart Befehl, funktional identisch zu /maintena restart
        dispatcher.register(buildRestartTree(false));

        // Eigenständiger /announce Befehl, funktional identisch zu /maintena announce
        dispatcher.register(AnnouncementCommandHandler.buildAnnounceTree());

        // Eigenständige Ban-Befehle, funktional identisch zu /maintena ban|unban|banlist
        dispatcher.register(BanCommandHandler.buildBanTree());
        dispatcher.register(BanCommandHandler.buildUnbanTree());
        dispatcher.register(BanCommandHandler.buildBanlistTree());

        // Eigenständige IP-Ban-Befehle, funktional identisch zu /maintena ip|ipban|ipunban|ipbanlist
        dispatcher.register(IpBanCommandHandler.buildIpTree());
        dispatcher.register(IpBanCommandHandler.buildIpBanTree());
        dispatcher.register(IpBanCommandHandler.buildIpUnbanTree());
        dispatcher.register(IpBanCommandHandler.buildIpBanlistTree());

        // Eigenständige Mute-Befehle (Chat-Timeouts), funktional identisch zu /maintena mute|unmute|mutelist
        dispatcher.register(MuteCommandHandler.buildMuteTree());
        dispatcher.register(MuteCommandHandler.buildMuteTree("timeout"));
        dispatcher.register(MuteCommandHandler.buildUnmuteTree());
        dispatcher.register(MuteCommandHandler.buildMutelistTree());

        // Eigenständiger /vanish und Kurzform /v
        dispatcher.register(VanishCommandHandler.buildVanishTree("vanish"));
        dispatcher.register(VanishCommandHandler.buildVanishTree("v"));

        dispatcher.register(DisguiseCommandHandler.buildDisguiseTree());
        dispatcher.register(DisguiseCommandHandler.buildUndisguiseTree());

        // Eigenständige Freeze-Befehle, funktional identisch zu /maintena freeze|unfreeze
        dispatcher.register(FreezeCommandHandler.buildFreezeTree());
        dispatcher.register(FreezeCommandHandler.buildUnfreezeTree());

        // Eigenständiger Nick-Befehl, funktional identisch zu /maintena nick
        dispatcher.register(NickCommandHandler.buildNickTree());
    }

    // ==================== /maintena restart  &  /restart ====================

    /**
     * @param menu true = Variante für /maintena restart: ohne Argumente öffnet sich das Restart-Menü
     *             (statt sofort neu zu starten), "schedule add" ohne Argumente das Formular für Zeitpläne.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildRestartTree(boolean menu) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("restart").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                .executes(ctx -> {
                    RestartManager.getInstance().restartNow();

                    ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Restarting server now..."), true);

                    return 1;
                })

                .then(Commands.literal("in").then(Commands.argument("minutes", IntegerArgumentType.integer(1)).suggests(intSuggestions(MANUAL_MINUTES_SUGGESTIONS)).executes(ctx -> {
                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

                    RestartManager.getInstance().startManualCountdown(minutes);

                    ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Server will restart in " + minutes + " minute(s)"), true);

                    return 1;
                })))

                .then(Commands.literal("cancel").executes(ctx -> {
                    boolean cancelled = RestartManager.getInstance().cancelActiveCountdown();

                    if (cancelled) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Pending restart cancelled"), true);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§c✗ No restart is currently pending"));
                    }

                    return 1;
                }))

                .then(Commands.literal("schedule").then(menu ? buildScheduleAddTree().executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.RESTART_ADD)) : buildScheduleAddTree())

                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(RestartManager.getInstance().getSchedules().stream().map(schedule -> schedule.id), builder)).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean removed = RestartManager.getInstance().removeSchedule(name);

                            if (removed) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Removed scheduled restart \"" + name + "\""), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§c✗ No scheduled restart named \"" + name + "\""));
                            }

                            return 1;
                        })))

                        .then(Commands.literal("list").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(RestartManager.getInstance().getScheduleListText()), false);

                            return 1;
                        })));

        if (menu) {
            root.executes(ctx -> SettingsGUIHandler.open(ctx, SettingsGUIHandler.Page.RESTART));
        }

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildScheduleAddTree() {
        return Commands.literal("add")

                .then(Commands.literal("interval").then(Commands.argument("hours", IntegerArgumentType.integer(0)).suggests(intSuggestions(INTERVAL_HOURS_SUGGESTIONS)).then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59)).suggests(intSuggestions(INTERVAL_MINUTES_SUGGESTIONS)).executes(ctx -> addInterval(ctx, null)).then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> addInterval(ctx, StringArgumentType.getString(ctx, "name")))))))

                .then(Commands.literal("time").then(Commands.argument("hour", IntegerArgumentType.integer(0, 23)).suggests(intSuggestions(HOUR_SUGGESTIONS))

                        .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59)).suggests(intSuggestions(MINUTE_SUGGESTIONS))

                                .then(Commands.literal("daily").executes(ctx -> addDaily(ctx, null)).then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> addDaily(ctx, StringArgumentType.getString(ctx, "name")))))

                                .then(Commands.literal("weekly").then(Commands.argument("weekday", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(WEEKDAYS, builder)).executes(ctx -> addWeekly(ctx, null)).then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> addWeekly(ctx, StringArgumentType.getString(ctx, "name")))))))));
    }

    private static int addInterval(CommandContext<CommandSourceStack> ctx, String name) {
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

        long totalMinutes = hours * 60L + minutes;

        if (totalMinutes < 1) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Interval must be at least 1 minute"));
            return 0;
        }

        RestartSchedule schedule = RestartManager.getInstance().addIntervalSchedule(totalMinutes, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Scheduled restart \"" + schedule.id + "\" added: " + RestartManager.getInstance().describeSchedule(schedule)), true);

        return 1;
    }

    private static int addDaily(CommandContext<CommandSourceStack> ctx, String name) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        int minute = IntegerArgumentType.getInteger(ctx, "minute");

        RestartSchedule schedule = RestartManager.getInstance().addTimeSchedule(hour, minute, RestartSchedule.Frequency.DAILY, null, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Scheduled restart \"" + schedule.id + "\" added: daily at " + formatTime(hour, minute)), true);

        return 1;
    }

    private static int addWeekly(CommandContext<CommandSourceStack> ctx, String name) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        int minute = IntegerArgumentType.getInteger(ctx, "minute");

        String weekdayStr = StringArgumentType.getString(ctx, "weekday");
        DayOfWeek weekday = parseWeekday(weekdayStr);

        if (weekday == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Unknown weekday: " + weekdayStr));
            return 0;
        }

        RestartSchedule schedule = RestartManager.getInstance().addTimeSchedule(hour, minute, RestartSchedule.Frequency.WEEKLY, weekday, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Scheduled restart \"" + schedule.id + "\" added: every " + capitalize(weekdayStr) + " at " + formatTime(hour, minute)), true);

        return 1;
    }

    private static DayOfWeek parseWeekday(String raw) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equalsIgnoreCase(raw)) {
                return day;
            }
        }

        return null;
    }

    private static String formatTime(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
