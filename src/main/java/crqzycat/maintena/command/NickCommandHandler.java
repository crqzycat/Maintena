package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.nick.NickManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Map;
import java.util.UUID;

/**
 * Nick-Befehle (nur für Spieler im Spiel, Gamemaster/OP):
 *   /nick <name>          Nickname setzen
 *   /nick reset, /unnick  Nickname entfernen
 *   /nick status          eigenen Nickname anzeigen
 *   /nick list            alle aktuellen Nicknames
 */
public final class NickCommandHandler {

    private NickCommandHandler() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildNickTree() {
        return Commands.literal("nick")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reset").executes(NickCommandHandler::reset))
                .then(Commands.literal("status").executes(NickCommandHandler::status))
                .then(Commands.literal("list").executes(NickCommandHandler::list))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(NickCommandHandler::set));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUnnickTree() {
        return Commands.literal("unnick")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(NickCommandHandler::reset);
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        String nick = StringArgumentType.getString(ctx, "name").trim();
        String error = NickManager.getInstance().validate(player, nick);

        if (error != null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ " + error));
            return 0;
        }

        if (!NickManager.getInstance().set(player, nick)) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Could not set the nickname"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§b✓ Other players now see you as §f" + nick), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        String nick = NickManager.getInstance().get(player);

        ctx.getSource().sendSuccess(() -> Component.literal(nick == null
                ? "§7You have no nickname"
                : "§bYour nickname is §f" + nick), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Map<UUID, String> all = NickManager.getInstance().all();

        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Nobody has a nickname"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§bNicknames §7(" + all.size() + ")§b:"), false);

        for (Map.Entry<UUID, String> entry : all.entrySet()) {
            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
            String ownerName = owner != null ? owner.getName().getString() : entry.getKey().toString();

            ctx.getSource().sendSuccess(
                    () -> Component.literal("§7- §f" + ownerName + " §7is shown as §f" + entry.getValue()), false);
        }

        return all.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        if (!NickManager.getInstance().reset(player)) {
            ctx.getSource().sendFailure(Component.literal("§c✗ You have no nickname"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Your nickname was removed"), false);
        return 1;
    }
}
