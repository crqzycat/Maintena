package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.disguise.DisguiseType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/** /disguise and /undisguise commands. */
public final class DisguiseCommandHandler {
    private DisguiseCommandHandler() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildDisguiseTree() {
        return Commands.literal("disguise")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("status").executes(DisguiseCommandHandler::status))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                if (player != ctx.getSource().getEntity()) builder.suggest(player.getName().getString());
                            }
                            builder.suggest("zombie");
                            builder.suggest("skeleton");
                            builder.suggest("creeper");
                            builder.suggest("cow");
                            builder.suggest("pig");
                            builder.suggest("chicken");
                            return builder.buildFuture();
                        })
                        .executes(DisguiseCommandHandler::disguise));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildUndisguiseTree() {
        return Commands.literal("undisguise")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(DisguiseCommandHandler::undisguise);
    }

    private static int disguise(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        String target = StringArgumentType.getString(ctx, "target");
        DisguiseManager manager = DisguiseManager.getInstance();

        ServerPlayer targetPlayer = ctx.getSource().getServer().getPlayerList().getPlayerByName(target);
        if (targetPlayer != null) {
            if (!manager.disguisePlayer(player, targetPlayer)) {
                ctx.getSource().sendFailure(Component.literal("§c✗ Could not disguise as that player"));
                return 0;
            }

            ctx.getSource().sendSuccess(() -> Component.literal("§b✓ You are now disguised as §f" + targetPlayer.getName().getString()), false);
            return 1;
        }

        Optional<EntityType<?>> type = DisguiseType.find(target);
        if (type.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Unknown entity or player: §f" + target));
            return 0;
        }

        if (!manager.disguiseMob(player, type.get())) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This entity cannot be used as a disguise"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§b✓ You are now disguised as §f" + target.replace("minecraft:", "")), false);
        return 1;
    }

    private static int undisguise(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        if (!DisguiseManager.getInstance().undisguise(player)) {
            ctx.getSource().sendFailure(Component.literal("§c✗ You are not disguised"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ You are no longer disguised"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        DisguiseManager manager = DisguiseManager.getInstance();
        String playerName = manager.getPlayerDisguiseName(player);

        if (playerName != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§bYou are disguised as player §f" + playerName), false);
            return 1;
        }

        EntityType<?> type = manager.getDisguise(player) != null ? manager.getDisguise(player).getType() : null;
        ctx.getSource().sendSuccess(() -> Component.literal(type == null ? "§7You are not disguised" : "§bYou are disguised as §f" + type.getDescription().getString()), false);
        return 1;
    }
}
