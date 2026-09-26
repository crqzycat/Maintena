package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.maintenance.MaintenanceManager;
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
 * Globaler /maintena Befehl: Reload für Maintenance + Restart, sowie alle
 * restart-bezogenen Sub-Commands (/maintena restart ...).
 */
public class MaintenaCommandHandler {

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private static final List<String> INTERVAL_SUGGESTIONS =
            List.of("15", "30", "60", "180", "360", "720", "1440");

    private static final List<String> MANUAL_MINUTES_SUGGESTIONS =
            List.of("1", "5", "10", "15", "30", "60");

    private static final List<String> HOUR_SUGGESTIONS =
            List.of("0", "4", "6", "12", "18", "22");

    private static final List<String> MINUTE_SUGGESTIONS =
            List.of("0", "15", "30", "45");

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> registerMaintenaCommand(dispatcher)
        );
    }

    private static void registerMaintenaCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("maintena")
                        .requires(source -> source.permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))

                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    MaintenanceManager.getInstance().reload();
                                    RestartManager.getInstance().reload();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§a✓ Maintena config reloaded"),
                                            true
                                    );

                                    return 1;
                                })
                        )

                        .then(buildRestartTree())
        );
    }

    // ==================== /maintena restart ====================

    private static LiteralArgumentBuilder<CommandSourceStack> buildRestartTree() {
        return Commands.literal("restart")
                .then(Commands.literal("now")
                        .executes(ctx -> {
                            RestartManager.getInstance().restartNow();

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a✓ Restarting server now..."),
                                    true
                            );

                            return 1;
                        })
                )

                .then(Commands.literal("in")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(MANUAL_MINUTES_SUGGESTIONS, builder))
                                .executes(ctx -> {
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

                                    RestartManager.getInstance().startManualCountdown(minutes);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "§a✓ Server will restart in " + minutes + " minute(s)"
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            boolean cancelled = RestartManager.getInstance().cancelActiveCountdown();

                            if (cancelled) {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§a✓ Pending restart cancelled"),
                                        true
                                );
                            } else {
                                ctx.getSource().sendFailure(
                                        Component.literal("§c✗ No restart is currently pending")
                                );
                            }

                            return 1;
                        })
                )

                .then(Commands.literal("schedule")
                        .then(buildScheduleAddTree())

                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                RestartManager.getInstance().getSchedules()
                                                        .stream()
                                                        .map(schedule -> schedule.id),
                                                builder
                                        ))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean removed = RestartManager.getInstance().removeSchedule(name);

                                            if (removed) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal(
                                                                "§a✓ Removed scheduled restart \"" + name + "\""
                                                        ),
                                                        true
                                                );
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(
                                                                "§c✗ No scheduled restart named \"" + name + "\""
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                                )
                        )

                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    RestartManager.getInstance().getScheduleListText()
                                            ),
                                            false
                                    );

                                    return 1;
                                })
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildScheduleAddTree() {
        return Commands.literal("add")

                .then(Commands.literal("interval")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(INTERVAL_SUGGESTIONS, builder))
                                .executes(ctx -> addInterval(ctx, null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> addInterval(
                                                ctx, StringArgumentType.getString(ctx, "name")))
                                )
                        )
                )

                .then(Commands.literal("time")
                        .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(HOUR_SUGGESTIONS, builder))

                                .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59))
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(MINUTE_SUGGESTIONS, builder))

                                        .then(Commands.literal("daily")
                                                .executes(ctx -> addDaily(ctx, null))
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(ctx -> addDaily(
                                                                ctx, StringArgumentType.getString(ctx, "name")))
                                                )
                                        )

                                        .then(Commands.literal("weekly")
                                                .then(Commands.argument("weekday", StringArgumentType.word())
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(WEEKDAYS, builder))
                                                        .executes(ctx -> addWeekly(ctx, null))
                                                        .then(Commands.argument("name", StringArgumentType.word())
                                                                .executes(ctx -> addWeekly(
                                                                        ctx, StringArgumentType.getString(ctx, "name")))
                                                        )
                                                )
                                        )
                                )
                        )
                );
    }

    private static int addInterval(CommandContext<CommandSourceStack> ctx, String name) {
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

        RestartSchedule schedule = RestartManager.getInstance().addIntervalSchedule(minutes, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(
                    Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists")
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a✓ Scheduled restart \"" + schedule.id + "\" added: every " + minutes + " minute(s)"
                ),
                true
        );

        return 1;
    }

    private static int addDaily(CommandContext<CommandSourceStack> ctx, String name) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        int minute = IntegerArgumentType.getInteger(ctx, "minute");

        RestartSchedule schedule = RestartManager.getInstance()
                .addTimeSchedule(hour, minute, RestartSchedule.Frequency.DAILY, null, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(
                    Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists")
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a✓ Scheduled restart \"" + schedule.id + "\" added: daily at "
                                + formatTime(hour, minute)
                ),
                true
        );

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

        RestartSchedule schedule = RestartManager.getInstance()
                .addTimeSchedule(hour, minute, RestartSchedule.Frequency.WEEKLY, weekday, name);

        if (schedule == null) {
            ctx.getSource().sendFailure(
                    Component.literal("§c✗ A scheduled restart named \"" + name + "\" already exists")
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a✓ Scheduled restart \"" + schedule.id + "\" added: every "
                                + capitalize(weekdayStr) + " at " + formatTime(hour, minute)
                ),
                true
        );

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
