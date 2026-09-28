package crqzycat.maintena.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.announcement.AnnouncementSchedule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.time.DayOfWeek;
import java.util.List;

/**
 * /announce Befehlsbaum (wird auch unter /maintena announce eingehängt).
 *
 * Einmalig (nicht gespeichert):
 *   /announce chat <text>
 *   /announce screen <text>
 *
 * Geplant (wie bei /restart schedule):
 *   /announce schedule add interval <hours> <minutes> <chat|screen> <name> <text>
 *   /announce schedule add time <hour> <minute> daily <chat|screen> <name> <text>
 *   /announce schedule add time <hour> <minute> weekly <weekday> <chat|screen> <name> <text>
 *   /announce schedule remove <name>
 *   /announce schedule list
 *
 * In Texten wird "&a" usw. zu Farbcodes. Bei "screen" trennt " | " Titel und Untertitel.
 */
public class AnnouncementCommandHandler {

    private static final List<String> WEEKDAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    private static final List<String> DISPLAY_SUGGESTIONS = List.of("chat", "screen");

    private static final List<Integer> INTERVAL_HOURS_SUGGESTIONS =
            List.of(0, 1, 2, 3, 6, 12, 24);

    private static final List<Integer> INTERVAL_MINUTES_SUGGESTIONS =
            List.of(0, 15, 30, 45);

    private static final List<Integer> HOUR_SUGGESTIONS =
            List.of(0, 6, 12, 18, 23);

    private static final List<Integer> MINUTE_SUGGESTIONS =
            List.of(0, 15, 30, 45);

    public static LiteralArgumentBuilder<CommandSourceStack> buildAnnounceTree() {
        return Commands.literal("announce")
                .requires(source -> source.permissions()
                        .hasPermission(Permissions.COMMANDS_GAMEMASTER))

                // Einmalige Announcements ohne Schedule
                .then(Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> sendOnce(ctx, AnnouncementSchedule.Display.CHAT))
                        )
                )
                .then(Commands.literal("screen")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> sendOnce(ctx, AnnouncementSchedule.Display.SCREEN))
                        )
                )

                // Geplante Announcements
                .then(Commands.literal("schedule")
                        .then(buildScheduleAddTree())

                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                AnnouncementManager.getInstance().getSchedules()
                                                        .stream()
                                                        .map(schedule -> schedule.id),
                                                builder
                                        ))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean removed = AnnouncementManager.getInstance().removeSchedule(name);

                                            if (removed) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal(
                                                                "§a✓ Removed scheduled announcement \"" + name + "\""
                                                        ),
                                                        true
                                                );
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(
                                                                "§c✗ No scheduled announcement named \"" + name + "\""
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
                                                    AnnouncementManager.getInstance().getScheduleListText()
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
                        .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                .suggests(intSuggestions(INTERVAL_HOURS_SUGGESTIONS))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                        .suggests(intSuggestions(INTERVAL_MINUTES_SUGGESTIONS))
                                        .then(displayNameMessage(AnnouncementCommandHandler::addInterval))
                                )
                        )
                )

                .then(Commands.literal("time")
                        .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
                                .suggests(intSuggestions(HOUR_SUGGESTIONS))

                                .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59))
                                        .suggests(intSuggestions(MINUTE_SUGGESTIONS))

                                        .then(Commands.literal("daily")
                                                .then(displayNameMessage(AnnouncementCommandHandler::addDaily))
                                        )

                                        .then(Commands.literal("weekly")
                                                .then(Commands.argument("weekday", StringArgumentType.word())
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(WEEKDAYS, builder))
                                                        .then(displayNameMessage(AnnouncementCommandHandler::addWeekly))
                                                )
                                        )
                                )
                        )
                );
    }

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

    /**
     * Gemeinsamer Abschluss aller "add"-Varianten: <chat|screen> <name> <text>
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> displayNameMessage(
            Command<CommandSourceStack> executor
    ) {
        return Commands.argument("display", StringArgumentType.word())
                .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(DISPLAY_SUGGESTIONS, builder))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(executor)
                        )
                );
    }

    // ==================== Ausführung ====================

    private static int sendOnce(CommandContext<CommandSourceStack> ctx, AnnouncementSchedule.Display display) {
        String message = AnnouncementManager.colorize(StringArgumentType.getString(ctx, "message"));

        AnnouncementManager.getInstance().send(display, message);

        ctx.getSource().sendSuccess(
                () -> Component.literal("§a✓ Announcement sent (" + display.name().toLowerCase() + ")"),
                true
        );

        return 1;
    }

    private static int addInterval(CommandContext<CommandSourceStack> ctx) {
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

        long totalMinutes = hours * 60L + minutes;

        if (totalMinutes < 1) {
            ctx.getSource().sendFailure(
                    Component.literal("§c✗ Interval must be at least 1 minute")
            );
            return 0;
        }

        AnnouncementSchedule.Display display = readDisplay(ctx);
        if (display == null) {
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String message = AnnouncementManager.colorize(StringArgumentType.getString(ctx, "message"));

        AnnouncementSchedule schedule = AnnouncementManager.getInstance()
                .addIntervalSchedule(totalMinutes, display, name, message);

        return reportAdded(ctx, schedule, name);
    }

    private static int addDaily(CommandContext<CommandSourceStack> ctx) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        int minute = IntegerArgumentType.getInteger(ctx, "minute");

        AnnouncementSchedule.Display display = readDisplay(ctx);
        if (display == null) {
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String message = AnnouncementManager.colorize(StringArgumentType.getString(ctx, "message"));

        AnnouncementSchedule schedule = AnnouncementManager.getInstance().addTimeSchedule(
                hour, minute, AnnouncementSchedule.Frequency.DAILY, null, display, name, message
        );

        return reportAdded(ctx, schedule, name);
    }

    private static int addWeekly(CommandContext<CommandSourceStack> ctx) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        int minute = IntegerArgumentType.getInteger(ctx, "minute");

        String weekdayStr = StringArgumentType.getString(ctx, "weekday");
        DayOfWeek weekday = parseWeekday(weekdayStr);

        if (weekday == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Unknown weekday: " + weekdayStr));
            return 0;
        }

        AnnouncementSchedule.Display display = readDisplay(ctx);
        if (display == null) {
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String message = AnnouncementManager.colorize(StringArgumentType.getString(ctx, "message"));

        AnnouncementSchedule schedule = AnnouncementManager.getInstance().addTimeSchedule(
                hour, minute, AnnouncementSchedule.Frequency.WEEKLY, weekday, display, name, message
        );

        return reportAdded(ctx, schedule, name);
    }

    private static int reportAdded(
            CommandContext<CommandSourceStack> ctx,
            AnnouncementSchedule schedule,
            String name
    ) {
        if (schedule == null) {
            ctx.getSource().sendFailure(
                    Component.literal("§c✗ A scheduled announcement named \"" + name + "\" already exists")
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "§a✓ Scheduled announcement \"" + schedule.id + "\" added: "
                                + AnnouncementManager.getInstance().describeSchedule(schedule)
                                + " (" + schedule.display.name().toLowerCase() + ")"
                ),
                true
        );

        return 1;
    }

    private static AnnouncementSchedule.Display readDisplay(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "display");

        for (AnnouncementSchedule.Display display : AnnouncementSchedule.Display.values()) {
            if (display.name().equalsIgnoreCase(raw)) {
                return display;
            }
        }

        ctx.getSource().sendFailure(
                Component.literal("§c✗ Unknown display \"" + raw + "\" (use chat or screen)")
        );

        return null;
    }

    private static DayOfWeek parseWeekday(String raw) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equalsIgnoreCase(raw)) {
                return day;
            }
        }

        return null;
    }
}
