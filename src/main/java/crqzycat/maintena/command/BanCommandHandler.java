package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import crqzycat.maintena.ban.BanManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ban-Befehle:
 *   /ban <player> [reason]              permanenter Ban
 *   /ban <player> <dauer> [reason]      Ban auf Zeit (z.B. 7d, 24h, 30m, 1d12h)
 *   /ban info <player>                  Details zu einem Ban
 *   /unban <player>                     Ban aufheben
 *   /banlist [seite]                    alle aktiven Bans
 *
 * Die Vanilla-Befehle /ban und /banlist werden dafür aus dem Dispatcher entfernt und ersetzt.
 * /pardon, /ban-ip und /pardon-ip bleiben unverändert bestehen.
 */
public class BanCommandHandler {

    private static final int BANS_PER_PAGE = 8;

    private static final List<Integer> DURATION_NUMBER_SUGGESTIONS = List.of(1, 7, 30);

    /**
     * Entfernt die Vanilla-Befehle /ban und /banlist, damit unsere Versionen
     * nicht mit ihnen zusammengeführt werden (Brigadier merged gleichnamige Knoten).
     */
    @SuppressWarnings("unchecked")
    public static void removeVanillaCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Field children = CommandNode.class.getDeclaredField("children");
            Field literals = CommandNode.class.getDeclaredField("literals");
            children.setAccessible(true);
            literals.setAccessible(true);

            Object root = dispatcher.getRoot();
            Map<String, ?> childMap = (Map<String, ?>) children.get(root);
            Map<String, ?> literalMap = (Map<String, ?>) literals.get(root);

            for (String name : new String[]{"ban", "banlist"}) {
                childMap.remove(name);
                literalMap.remove(name);
            }
        } catch (ReflectiveOperationException e) {
            System.err.println("[Maintena] Could not remove vanilla /ban and /banlist commands");
            e.printStackTrace();
        }
    }

    // ==================== Befehlsbäume ====================

    public static LiteralArgumentBuilder<CommandSourceStack> buildBanTree() {
        return Commands.literal("ban")
                .requires(BanCommandHandler::hasPermission)

                .then(Commands.literal("info")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(BanCommandHandler::suggestBannedNames)
                                .executes(BanCommandHandler::showInfo)
                        )
                )

                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .suggests(BanCommandHandler::suggestPlayerNames)
                        .executes(ctx -> ban(ctx, ""))
                        .then(Commands.argument("details", StringArgumentType.greedyString())
                                .suggests(BanCommandHandler::suggestDuration)
                                .executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "details")))
                        )
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnbanTree() {
        return Commands.literal("unban")
                .requires(BanCommandHandler::hasPermission)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(BanCommandHandler::suggestBannedNames)
                        .executes(BanCommandHandler::unban)
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBanlistTree() {
        return Commands.literal("banlist")
                .requires(BanCommandHandler::hasPermission)
                .executes(ctx -> showBanlist(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showBanlist(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                );
    }

    private static boolean hasPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // ==================== Ausführung ====================

    private static int ban(CommandContext<CommandSourceStack> ctx, String details) throws CommandSyntaxException {
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
            source.sendFailure(Component.literal("§c✗ You can't ban yourself"));
            return 0;
        }

        // Operatoren lassen sich nur über die Konsole bannen
        if (executor != null && server.getPlayerList().isOp(target)) {
            source.sendFailure(Component.literal("§c✗ Operators can only be banned from the console"));
            return 0;
        }

        // Dauer und Grund trennen: ist das erste Wort eine Dauer, wird es ein Temp-Ban
        String trimmed = details.trim();
        Long duration = null;
        String reason = trimmed.isEmpty() ? null : trimmed;

        if (!trimmed.isEmpty()) {
            String[] parts = trimmed.split("\\s+", 2);

            if (BanManager.looksLikeDuration(parts[0])) {
                duration = BanManager.parseDuration(parts[0]);

                if (duration == null) {
                    source.sendFailure(Component.literal(
                            "§c✗ Invalid duration \"" + parts[0] + "\" (use e.g. 30m, 24h, 7d, 1d12h)"
                    ));
                    return 0;
                }

                reason = parts.length > 1 ? parts[1] : null;
            }
        }

        final Long finalDuration = duration;
        final String finalReason = reason;

        boolean existed = BanManager.getInstance()
                .ban(server, target, finalDuration, finalReason, source.getTextName());

        String shownReason = finalReason != null
                ? finalReason
                : BanManager.getInstance().getConfig().defaultReason;

        source.sendSuccess(
                () -> Component.literal(
                        "§a✓ " + (existed ? "Updated ban of " : "Banned ") + target.name()
                                + (finalDuration == null
                                ? " permanently"
                                : " for " + BanManager.formatDuration(finalDuration))
                                + "§7: §f" + shownReason
                ),
                true
        );

        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");

        boolean removed = BanManager.getInstance().unban(ctx.getSource().getServer(), name);

        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not banned"));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("§a✓ Unbanned " + name),
                true
        );

        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        BanManager manager = BanManager.getInstance();

        UserBanListEntry entry = manager.findBan(ctx.getSource().getServer(), name);

        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not banned"));
            return 0;
        }

        boolean permanent = entry.getExpires() == null;

        StringBuilder sb = new StringBuilder("§6=== Ban: §e" + entry.getUser().name() + " §6===");
        sb.append("\n§7Type: §f").append(permanent ? "permanent" : "temporary");
        sb.append("\n§7Reason: §f").append(entry.getReason() != null
                ? entry.getReason()
                : manager.getConfig().defaultReason);
        sb.append("\n§7Banned by: §f").append(entry.getSource() != null ? entry.getSource() : "-");
        sb.append("\n§7Banned on: §f").append(manager.formatDate(entry.getCreated()));

        if (!permanent) {
            sb.append("\n§7Expires: §f").append(manager.formatDate(entry.getExpires()));
            sb.append("\n§7Time remaining: §e").append(manager.describeRemaining(entry));
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    private static int showBanlist(CommandContext<CommandSourceStack> ctx, int page) {
        BanManager manager = BanManager.getInstance();
        List<UserBanListEntry> bans = manager.getActiveBans(ctx.getSource().getServer());

        if (bans.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eNo active bans."), false);
            return 1;
        }

        int pages = (bans.size() + BANS_PER_PAGE - 1) / BANS_PER_PAGE;

        if (page > pages) {
            ctx.getSource().sendFailure(Component.literal("§c✗ There are only " + pages + " page(s)"));
            return 0;
        }

        StringBuilder sb = new StringBuilder(
                "§6=== Active Bans (" + bans.size() + ") — page " + page + "/" + pages + " ==="
        );

        int from = (page - 1) * BANS_PER_PAGE;
        int to = Math.min(from + BANS_PER_PAGE, bans.size());

        for (UserBanListEntry entry : bans.subList(from, to)) {
            sb.append("\n§e- §f").append(entry.getUser().name())
                    .append(" §7[").append(manager.describeRemaining(entry)).append("] §7— ")
                    .append(entry.getReason() != null ? entry.getReason() : manager.getConfig().defaultReason);
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    // ==================== Vorschläge ====================

    private static CompletableFuture<Suggestions> suggestPlayerNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestBannedNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
                BanManager.getInstance().getActiveBans(ctx.getSource().getServer())
                        .stream()
                        .map(entry -> entry.getUser().name()),
                builder
        );
    }

    /**
     * Erst Zahlen (numerisch sortiert), nach einer Zahl dann die Einheiten: 7d, 7h, 7m.
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
        }

        return builder.buildFuture();
    }
}
