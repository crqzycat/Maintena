package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.freeze.FreezeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * Freeze-Befehle (Gamemaster/OP):
 *   /freeze <player> [reason]   Spieler einfrieren (muss online sein)
 *   /freeze list                alle eingefrorenen Spieler
 *   /unfreeze <player>          Spieler wieder freigeben
 *   /unfreeze all               alle freigeben
 */
public class FreezeCommandHandler {

    public static LiteralArgumentBuilder<CommandSourceStack> buildFreezeTree() {
        return Commands.literal("freeze")
                .requires(FreezeCommandHandler::hasPermission)

                .then(Commands.literal("list").executes(FreezeCommandHandler::list))

                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                                        .filter(player -> !FreezeManager.getInstance().isFrozen(player))
                                        .map(player -> player.nameAndId().name()),
                                builder))
                        .executes(ctx -> freeze(ctx, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> freeze(ctx, StringArgumentType.getString(ctx, "reason")))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnfreezeTree() {
        return Commands.literal("unfreeze")
                .requires(FreezeCommandHandler::hasPermission)

                .then(Commands.literal("all").executes(FreezeCommandHandler::unfreezeAll))

                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                FreezeManager.getInstance().getFrozen().stream().map(entry -> entry.name),
                                builder))
                        .executes(FreezeCommandHandler::unfreeze));
    }

    private static boolean hasPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static int freeze(CommandContext<CommandSourceStack> ctx, String reason) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(ctx, "player");

        ServerPlayer target = server.getPlayerList().getPlayerByName(name);

        if (target == null) {
            source.sendFailure(Component.literal("§c✗ " + name + " is not online"));
            return 0;
        }

        ServerPlayer executor = source.getPlayer();

        if (executor != null && executor.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("§c✗ You can't freeze yourself"));
            return 0;
        }

        // Operatoren lassen sich nur über die Konsole einfrieren (wie beim Ban und Mute)
        if (executor != null && server.getPlayerList().isOp(target.nameAndId())) {
            source.sendFailure(Component.literal("§c✗ Operators can only be frozen from the console"));
            return 0;
        }

        FreezeManager manager = FreezeManager.getInstance();

        if (!manager.freeze(target, source.getTextName(), reason)) {
            source.sendFailure(Component.literal("§c✗ " + target.nameAndId().name() + " is already frozen"));
            return 0;
        }

        FreezeManager.Entry entry = manager.find(target.getUUID());

        source.sendSuccess(
                () -> Component.literal("§a✓ Froze " + entry.name + "§7: §f" + entry.reason),
                true
        );

        return 1;
    }

    private static int unfreeze(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");

        if (!FreezeManager.getInstance().unfreeze(ctx.getSource().getServer(), name)) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + name + " is not frozen"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Unfroze " + name), true);

        return 1;
    }

    private static int unfreezeAll(CommandContext<CommandSourceStack> ctx) {
        int count = FreezeManager.getInstance().unfreezeAll(ctx.getSource().getServer());

        if (count == 0) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Nobody is frozen"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Unfroze " + count + " player(s)"), true);

        return count;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<FreezeManager.Entry> frozen = FreezeManager.getInstance().getFrozen();

        if (frozen.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNobody is frozen."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6Frozen players (" + frozen.size() + "):"), false);

        for (FreezeManager.Entry entry : frozen) {
            boolean online = source.getServer().getPlayerList().getPlayer(entry.id) != null;

            source.sendSuccess(
                    () -> Component.literal("§7- §f" + entry.name + (online ? "" : " §c(offline)")
                            + " §7by §f" + entry.source + "§7: §f" + entry.reason),
                    false
            );
        }

        return frozen.size();
    }
}
