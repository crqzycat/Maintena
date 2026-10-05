package crqzycat.maintena.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * Vanish-Befehle (nur für Spieler im Spiel, Gamemaster/OP):
 *   /vanish, /v          Vanish umschalten
 *   /vanish on|off       Vanish gezielt ein- bzw. ausschalten
 *   /vanish status       eigenen Status anzeigen
 *   /vanish list         alle Spieler im Vanish
 */
public class VanishCommandHandler {

    public static LiteralArgumentBuilder<CommandSourceStack> buildVanishTree(String name) {
        return Commands.literal(name)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                .executes(ctx -> set(ctx, null))

                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .then(Commands.literal("status").executes(VanishCommandHandler::status))
                .then(Commands.literal("list").executes(VanishCommandHandler::list));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, Boolean value) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("§c✗ Vanish can only be used by a player in-game"));
            return 0;
        }

        VanishManager manager = VanishManager.getInstance();
        boolean target = value != null ? value : !manager.isVanished(player);

        if (!manager.setVanished(player, target)) {
            source.sendFailure(Component.literal(
                    target ? "§c✗ You are already vanished" : "§c✗ You are not vanished"));
            return 0;
        }

        // Bewusst ohne Admin-Broadcast, damit andere Ops nicht jedes Mal benachrichtigt werden
        source.sendSuccess(
                () -> Component.literal(target
                        ? "§b✓ You are now vanished"
                        : "§a✓ You are now visible again"),
                false
        );

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("§c✗ Vanish can only be used by a player in-game"));
            return 0;
        }

        boolean vanished = VanishManager.getInstance().isVanished(player);

        source.sendSuccess(
                () -> Component.literal(vanished ? "§bYou are vanished." : "§7You are visible."),
                false
        );

        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        List<String> names = VanishManager.getInstance().getOnlineVanished(source.getServer())
                .stream()
                .map(player -> player.nameAndId().name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        source.sendSuccess(
                () -> Component.literal(names.isEmpty()
                        ? "§eNobody is vanished."
                        : "§6Vanished (" + names.size() + "): §f" + String.join("§7, §f", names)),
                false
        );

        return 1;
    }
}
