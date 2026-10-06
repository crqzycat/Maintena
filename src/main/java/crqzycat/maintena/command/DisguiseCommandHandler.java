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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /disguise and /undisguise commands.
 * <pre>
 *   /disguise &lt;player|entity&gt;   disguise yourself
 *   /disguise status            show your current disguise
 *   /disguise list              show everybody who is disguised
 *   /undisguise                 remove your disguise
 * </pre>
 * Every disguise / undisguise is written to the server log (audit trail).
 */
public final class DisguiseCommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    private DisguiseCommandHandler() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildDisguiseTree() {
        return Commands.literal("disguise")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("status").executes(DisguiseCommandHandler::status))
                .then(Commands.literal("list").executes(DisguiseCommandHandler::list))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String typed = builder.getRemainingLowerCase();

                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                String name = player.getName().getString();

                                if (player != ctx.getSource().getEntity()
                                        && name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                                    builder.suggest(name);
                                }
                            }

                            for (String id : DisguiseType.suggestions(typed)) {
                                builder.suggest(id);
                            }

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
            if (targetPlayer == player) {
                ctx.getSource().sendFailure(Component.literal("§c✗ You cannot disguise as yourself"));
                return 0;
            }

            if (!manager.disguisePlayer(player, targetPlayer)) {
                ctx.getSource().sendFailure(Component.literal("§c✗ Could not disguise as that player"));
                return 0;
            }

            String targetName = targetPlayer.getName().getString();
            LOGGER.info("[Disguise] {} disguised as player {}", player.getName().getString(), targetName);
            ctx.getSource().sendSuccess(() -> Component.literal("§b✓ You are now disguised as §f" + targetName), false);
            return 1;
        }

        Optional<EntityType<?>> type = DisguiseType.find(target);
        if (type.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Unknown entity or player: §f" + target));
            return 0;
        }

        if (!DisguiseType.isAllowed(type.get())) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This entity is not allowed as a disguise"));
            return 0;
        }

        if (!manager.disguiseMob(player, type.get())) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This entity cannot be used as a disguise"));
            return 0;
        }

        String shown = type.get().getDescription().getString();
        LOGGER.info("[Disguise] {} disguised as {}", player.getName().getString(), shown);
        ctx.getSource().sendSuccess(() -> Component.literal("§b✓ You are now disguised as §f" + shown), false);
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

        LOGGER.info("[Disguise] {} removed his disguise", player.getName().getString());
        ctx.getSource().sendSuccess(() -> Component.literal("§a✓ You are no longer disguised"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("§c✗ This command can only be used in-game"));
            return 0;
        }

        DisguiseManager manager = DisguiseManager.getInstance();
        String playerName = manager.getPlayerDisguiseName(player);

        if (playerName != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§bYou are disguised as player §f" + playerName), false);
            return 1;
        }

        EntityType<?> type = manager.getDisguiseType(player);
        ctx.getSource().sendSuccess(() -> Component.literal(type == null
                ? "§7You are not disguised"
                : "§bYou are disguised as §f" + type.getDescription().getString()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Map<UUID, String> all = DisguiseManager.getInstance().describeAll();

        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Nobody is disguised"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§bDisguised players §7(" + all.size() + ")§b:"), false);

        for (Map.Entry<UUID, String> entry : all.entrySet()) {
            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
            String ownerName = owner != null ? owner.getName().getString() : entry.getKey().toString();

            ctx.getSource().sendSuccess(
                    () -> Component.literal("§7- §f" + ownerName + " §7as §f" + entry.getValue()), false);
        }

        return all.size();
    }
}
