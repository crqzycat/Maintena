package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.ban.IpBanManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.IpBanListEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * IP-Ban-Befehle, funktional identisch zu BanCommandHandler, nur für IP-Adressen statt Namen:
 *   /ip <player>                            zeigt die aktuelle IP eines online Spielers
 *   /ipban <player|ip> [reason]             permanenter IP-Ban
 *   /ipban <player|ip> <dauer> [reason]     IP-Ban auf Zeit (z.B. 7d, 24h, 30m, 1d12h)
 *   /ipunban <player|ip>                    IP-Ban aufheben
 *   /ipbanlist [seite]                      alle aktiven IP-Bans
 *
 * <player> muss online sein, damit die IP ermittelt werden kann; alternativ kann eine
 * IP-Adresse direkt angegeben werden. Der Vanilla-Befehl /ban-ip wird entfernt, /pardon-ip
 * bleibt bestehen und wirkt auf dieselbe Liste.
 */
public class IpBanCommandHandler {

    private static final int BANS_PER_PAGE = 8;
    private static final List<Integer> DURATION_NUMBER_SUGGESTIONS = List.of(1, 7, 30);

    public static LiteralArgumentBuilder<CommandSourceStack> buildIpTree() {
        return Commands.literal("ip")
                .requires(IpBanCommandHandler::hasPermission)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(IpBanCommandHandler::suggestOnlinePlayers)
                        .executes(IpBanCommandHandler::showIp)
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildIpBanTree() {
        return Commands.literal("ipban")
                .requires(IpBanCommandHandler::hasPermission)

                .then(Commands.literal("info")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(IpBanCommandHandler::suggestBannedIps)
                                .executes(IpBanCommandHandler::showInfo)
                        )
                )

                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(IpBanCommandHandler::suggestOnlinePlayers)
                        .executes(ctx -> ban(ctx, ""))
                        .then(Commands.argument("details", StringArgumentType.greedyString())
                                .suggests(IpBanCommandHandler::suggestDuration)
                                .executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "details")))
                        )
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildIpUnbanTree() {
        return Commands.literal("ipunban")
                .requires(IpBanCommandHandler::hasPermission)
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(IpBanCommandHandler::suggestBannedIps)
                        .executes(IpBanCommandHandler::unban)
                );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildIpBanlistTree() {
        return Commands.literal("ipbanlist")
                .requires(IpBanCommandHandler::hasPermission)
                .executes(ctx -> showBanlist(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showBanlist(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                );
    }

    private static boolean hasPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // ==================== Ausführung ====================

    private static int showIp(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        ServerPlayer player = server.getPlayerList().getPlayerByName(name);

        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not online"));
            return 0;
        }

        String ip = IpBanManager.getIpAddress(player);

        ctx.getSource().sendSuccess(
                () -> Component.literal("§6" + name + "§7's IP address: §f" + ip),
                false
        );

        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> ctx, String details) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String target = StringArgumentType.getString(ctx, "target");

        String ip = IpBanManager.getInstance().resolveIp(server, target);

        if (ip == null) {
            source.sendFailure(Component.literal(
                    "§c✗ " + target + " is not online and not a valid IP address"
            ));
            return 0;
        }

        // Dauer und Grund trennen, genau wie bei /ban
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
        final String finalIp = ip;

        boolean existed = IpBanManager.getInstance()
                .ban(server, ip, finalDuration, finalReason, source.getTextName());

        String shownReason = finalReason != null
                ? finalReason
                : BanManager.getInstance().getConfig().defaultReason;

        source.sendSuccess(
                () -> Component.literal(
                        "§a✓ " + (existed ? "Updated IP ban of " : "Banned IP ") + finalIp
                                + " §7(" + target + ")"
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
        MinecraftServer server = ctx.getSource().getServer();
        String target = StringArgumentType.getString(ctx, "target");

        String ip = IpBanManager.getInstance().resolveIp(server, target);
        boolean removed = ip != null && IpBanManager.getInstance().unban(server, ip);

        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + target + " is not IP banned"));
            return 0;
        }

        final String finalIp = ip;

        ctx.getSource().sendSuccess(
                () -> Component.literal("§a✓ Removed IP ban of " + finalIp),
                true
        );

        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String target = StringArgumentType.getString(ctx, "target");

        String ip = IpBanManager.getInstance().resolveIp(server, target);
        IpBanListEntry entry = ip != null ? IpBanManager.getInstance().findBan(server, ip) : null;

        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + target + " is not IP banned"));
            return 0;
        }

        BanManager manager = BanManager.getInstance();
        boolean permanent = entry.getExpires() == null;

        StringBuilder sb = new StringBuilder("§6=== IP Ban: §e" + entry.getUser() + " §6===");
        sb.append("\n§7Type: §f").append(permanent ? "permanent" : "temporary");
        sb.append("\n§7Reason: §f").append(entry.getReason() != null
                ? entry.getReason()
                : manager.getConfig().defaultReason);
        sb.append("\n§7Banned by: §f").append(entry.getSource() != null ? entry.getSource() : "-");
        sb.append("\n§7Banned on: §f").append(manager.formatDate(entry.getCreated()));

        if (!permanent) {
            sb.append("\n§7Expires: §f").append(manager.formatDate(entry.getExpires()));
            sb.append("\n§7Time remaining: §e")
                    .append(IpBanManager.getInstance().describeRemaining(entry));
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    private static int showBanlist(CommandContext<CommandSourceStack> ctx, int page) {
        MinecraftServer server = ctx.getSource().getServer();
        IpBanManager manager = IpBanManager.getInstance();
        List<IpBanListEntry> bans = manager.getActiveBans(server);

        if (bans.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eNo active IP bans."), false);
            return 1;
        }

        int pages = (bans.size() + BANS_PER_PAGE - 1) / BANS_PER_PAGE;

        if (page > pages) {
            ctx.getSource().sendFailure(Component.literal("§c✗ There are only " + pages + " page(s)"));
            return 0;
        }

        String defaultReason = BanManager.getInstance().getConfig().defaultReason;

        StringBuilder sb = new StringBuilder(
                "§6=== Active IP Bans (" + bans.size() + ") — page " + page + "/" + pages + " ==="
        );

        int from = (page - 1) * BANS_PER_PAGE;
        int to = Math.min(from + BANS_PER_PAGE, bans.size());

        for (IpBanListEntry entry : bans.subList(from, to)) {
            sb.append("\n§e- §f").append(entry.getUser())
                    .append(" §7[").append(manager.describeRemaining(entry)).append("] §7— ")
                    .append(entry.getReason() != null ? entry.getReason() : defaultReason);
        }

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);

        return 1;
    }

    // ==================== Vorschläge ====================

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestBannedIps(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
                IpBanManager.getInstance().getActiveBans(ctx.getSource().getServer())
                        .stream()
                        .map(IpBanListEntry::getUser),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestDuration(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String typed = builder.getRemaining();

        if (typed.contains(" ")) {
            return builder.buildFuture();
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
