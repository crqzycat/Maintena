package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.mute.MuteData;
import crqzycat.maintena.mute.MuteManager;
import crqzycat.maintena.util.PlayerNames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Chat-Timeout-Befehle (gemutete Spieler können nicht schreiben, Befehle gehen weiter):
 *   /mute <player> [reason]             permanenter Mute
 *   /mute <player> <dauer> [reason]     Mute auf Zeit (z.B. 30m, 24h, 7d, 1d12h)
 *   /mute info <player>                 Details zu einem Mute
 *   /unmute <player>                    Mute aufheben
 *   /mutelist [seite]                   alle aktiven Mutes
 *   /timeout ...                        Alias von /mute (gleiche Argumente)
 *
 * Dauer: Sekunden, Minuten, Stunden, Tage, kombinierbar (z.B. 10s, 10m, 1h, 1d12h).
 */
public class MuteCommandHandler {

    private static final int MUTES_PER_PAGE = 8;

    private static final List<Integer> DURATION_NUMBER_SUGGESTIONS = List.of(5, 30, 60);

    // ==================== Befehlsbäume ====================

    public static LiteralArgumentBuilder<CommandSourceStack> buildMuteTree() {
        return buildMuteTree("mute");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildMuteTree(String name) {
        return Commands.literal(name)
                .requires(MuteCommandHandler::hasPermission)

                .then(Commands.literal("info")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(MuteCommandHandler::suggestMutedNames)
                                .executes(MuteCommandHandler::showInfo)
                        )
                )

                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .suggests(MuteCommandHandler::suggestPlayerNames)
                        .executes(ctx -> mute(ctx, ""))
                        .then(Commands.argument("details", StringArgumentType.greedyString())
                                .suggests(MuteCommandHandler::suggestDuration)
                                .executes(ctx -> mute(ctx, StringArgumentType.getString(ctx, "details")))
                        )
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnmuteTree() {
        return Commands.literal("unmute")
                .requires(MuteCommandHandler::hasPermission)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(MuteCommandHandler::suggestMutedNames)
                        .executes(MuteCommandHandler::unmute)
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildMutelistTree() {
        return Commands.literal("mutelist")
                .requires(MuteCommandHandler::hasPermission)
                .executes(ctx -> showMutelist(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showMutelist(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                );
    }

    private static boolean hasPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // ==================== Ausführung ====================

    private static int mute(CommandContext<CommandSourceStack> ctx, String details) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(ctx, "player");

        if (targets.size() != 1) {
            source.sendFailure(Component.literal("§c✗ Please select exactly one player"));
            return 0;
        }

        NameAndId target = targets.iterator().next();

        ServerPlayer executor = source.getPlayer();

        if (executor != null && executor.nameAndId().id().equals(target.id())) {
            source.sendFailure(Component.literal("§c✗ You can't mute yourself"));
            return 0;
        }

        // Operatoren lassen sich nur über die Konsole muten (wie beim Ban)
        if (executor != null && server.getPlayerList().isOp(target)) {
            source.sendFailure(Component.literal("§c✗ Operators can only be muted from the console"));
            return 0;
        }

        // Dauer und Grund trennen: ist das erste Wort eine Dauer, wird es ein Mute auf Zeit
        String trimmed = details.trim();
        Long duration = null;
        String reason = trimmed.isEmpty() ? null : trimmed;

        if (!trimmed.isEmpty()) {
            String[] parts = trimmed.split("\\s+", 2);

            if (BanManager.looksLikeDuration(parts[0])) {
                duration = BanManager.parseDuration(parts[0]);

                if (duration == null) {
                    source.sendFailure(Component.literal(
                            "§c✗ Invalid duration \"" + parts[0] + "\" (use e.g. 30s, 10m, 1h, 7d, 1d12h)"
                    ));
                    return 0;
                }

                reason = parts.length > 1 ? parts[1] : null;
            }
        }

        final Long finalDuration = duration;
        final String finalReason = reason;

        MuteManager manager = MuteManager.getInstance();

        boolean existed = manager.mute(server, target, finalDuration, finalReason, source.getTextName());

        String shownReason = finalReason != null ? finalReason : manager.getConfig().defaultReason;

        source.sendSuccess(
                () -> Component.literal(
                        "§a✓ " + (existed ? "Updated mute of " : "Muted ") + target.name()
                                + (finalDuration == null
                                ? " permanently"
                                : " for " + BanManager.formatDuration(finalDuration))
                                + "§7: §f" + shownReason
                ),
                true
        );

        return 1;
    }

    private static int unmute(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");

        boolean removed = MuteManager.getInstance().unmute(ctx.getSource().getServer(), name);

        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not muted"));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("§a✓ Unmuted " + name),
                true
        );

        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        MuteManager manager = MuteManager.getInstance();

        MuteData.Entry entry = manager.findByName(name);

        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not muted"));
            return 0;
        }

        BanManager banManager = BanManager.getInstance();

        StringBuilder sb = new StringBuilder("§6=== Mute: §e" + entry.name + " §6===");
        sb.append("\n§7Type: §f").append(entry.isPermanent() ? "permanent" : "temporary");
        sb.append("\n§7Reason: §f").append(entry.reason != null ? entry.reason : manager.getConfig().defaultReason);
        sb.append("\n§7Muted by: §f").append(entry.source != null ? entry.source : "-");
        sb.append("\n§7Muted on: §f").append(banManager.formatDate(new Date(entry.created)));

        if (!entry.isPermanent()) {
            sb.append("\n§7Expires: §f").append(banManager.formatDate(new Date(entry.expires)));
            sb.append("\n§7Time remaining: §e").append(manager.describeRemaining(entry));
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    private static int showMutelist(CommandContext<CommandSourceStack> ctx, int page) {
        MuteManager manager = MuteManager.getInstance();
        List<MuteData.Entry> mutes = manager.getActiveMutes();

        if (mutes.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eNo active mutes."), false);
            return 1;
        }

        int pages = (mutes.size() + MUTES_PER_PAGE - 1) / MUTES_PER_PAGE;

        if (page > pages) {
            ctx.getSource().sendFailure(Component.literal("§c✗ There are only " + pages + " page(s)"));
            return 0;
        }

        StringBuilder sb = new StringBuilder(
                "§6=== Active Mutes (" + mutes.size() + ") — page " + page + "/" + pages + " ==="
        );

        int from = (page - 1) * MUTES_PER_PAGE;
        int to = Math.min(from + MUTES_PER_PAGE, mutes.size());

        for (MuteData.Entry entry : mutes.subList(from, to)) {
            sb.append("\n§e- §f").append(entry.name)
                    .append(" §7[").append(manager.describeRemaining(entry)).append("] §7— ")
                    .append(entry.reason != null ? entry.reason : manager.getConfig().defaultReason);
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    // ==================== Vorschläge ====================

    private static CompletableFuture<Suggestions> suggestPlayerNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        Set<String> muted = MuteManager.getInstance().getActiveMutes()
                .stream()
                .map(entry -> entry.name)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        return SharedSuggestionProvider.suggest(
                PlayerNames.known().stream().filter(name -> !muted.contains(name)).toList(),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestMutedNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
                MuteManager.getInstance().getActiveMutes().stream().map(entry -> entry.name),
                builder
        );
    }

    /**
     * Erst Zahlen (numerisch sortiert), nach einer Zahl dann die Einheiten: 5d, 5h, 5m.
     */
    private static CompletableFuture<Suggestions> suggestDuration(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining();

        if (typed.contains(" ")) {
            return builder.buildFuture(); // schon beim Grund
        }

        if (typed.isEmpty()) {
            for (int value : DURATION_NUMBER_SUGGESTIONS) {
                builder.suggest(value);
            }
        } else if (typed.matches("\\d+")) {
            builder.suggest(typed + "d");
            builder.suggest(typed + "h");
            builder.suggest(typed + "m");
            builder.suggest(typed + "s");
        }

        return builder.buildFuture();
    }
}
