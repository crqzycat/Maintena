package crqzycat.maintena.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.disguise.DisguiseType;
import crqzycat.maintena.disguise.SkinLookup;
import crqzycat.maintena.util.PlayerNames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * /disguise and /undisguise commands.
 * <pre>
 *   /disguise &lt;player|entity&gt;   disguise yourself (ANY Minecraft account works, online or not;
 *                               "player:&lt;name&gt;" forces a player when a name equals an entity id)
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
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                if (player != ctx.getSource().getEntity()) {
                                    names.add(player.getName().getString());
                                }
                            }

                            // everybody who was ever on the server (usercache) + whitelist
                            names.addAll(PlayerNames.known());

                            for (String name : names) {
                                if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
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

        String raw = StringArgumentType.getString(ctx, "target").trim();
        boolean forcePlayer = raw.regionMatches(true, 0, "player:", 0, 7);
        String target = forcePlayer ? raw.substring(7).trim() : raw;
        DisguiseManager manager = DisguiseManager.getInstance();

        if (target.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Unknown entity or player"));
            return 0;
        }

        // 1) Player that is online right now
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(target);
        if (online != null) {
            if (online == player) {
                ctx.getSource().sendFailure(Component.literal("§c✗ You cannot disguise as yourself"));
                return 0;
            }

            GameProfile profile = online.getGameProfile();

            // Offline-mode servers have no skin data: fetch the real skin from Mojang instead
            if (!profile.properties().containsKey("textures")) {
                return lookup(ctx, player, profile.name(), profile);
            }

            return applyProfile(player, profile) ? 1 : 0;
        }

        // 2) Entity (every entity except items and a few technical ones)
        if (!forcePlayer) {
            Optional<EntityType<?>> type = DisguiseType.find(target);

            if (type.isPresent()) {
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
        }

        // 3) Any other Minecraft account: online or offline, joined before or never
        if (SkinLookup.isValidName(target)) {
            return lookup(ctx, player, target, null);
        }

        ctx.getSource().sendFailure(Component.literal("§c✗ Unknown entity or player: §f" + target));
        return 0;
    }

    /**
     * Asks Mojang for the account (asynchronously, the server keeps running) and disguises the player
     * as soon as the answer is there.
     *
     * @param fallback profile to use if Mojang doesn't know the name or can't be reached (may be null)
     */
    private static int lookup(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String name, GameProfile fallback) {
        MinecraftServer server = ctx.getSource().getServer();
        UUID playerId = player.getUUID();

        ctx.getSource().sendSuccess(() -> Component.literal("§7Looking up §f" + name + "§7 ..."), false);

        SkinLookup.lookup(name).whenComplete((result, error) -> server.execute(() -> {
            ServerPlayer current = server.getPlayerList().getPlayer(playerId);

            if (current == null) {
                return; // left while we were waiting
            }

            if (error != null) {
                LOGGER.warn("[Disguise] Mojang lookup for '{}' failed: {}", name, String.valueOf(error.getMessage()));

                if (fallback != null) {
                    applyProfile(current, fallback);
                } else {
                    current.sendSystemMessage(Component.literal(
                            "§c✗ Could not look up §f" + name + " §c(Mojang API not reachable or rate limited)"));
                }

                return;
            }

            if (result.isPresent()) {
                applyProfile(current, result.get());
            } else if (fallback != null) {
                applyProfile(current, fallback);
            } else {
                current.sendSystemMessage(Component.literal("§c✗ Unknown entity or player: §f" + name));
            }
        }));

        return 1;
    }

    private static boolean applyProfile(ServerPlayer player, GameProfile profile) {
        if (profile.id().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c✗ You cannot disguise as yourself"));
            return false;
        }

        if (!DisguiseManager.getInstance().disguiseAsProfile(player, profile)) {
            player.sendSystemMessage(Component.literal("§c✗ Could not disguise as that player"));
            return false;
        }

        LOGGER.info("[Disguise] {} disguised as player {}", player.getName().getString(), profile.name());
        player.sendSystemMessage(Component.literal("§b✓ You are now disguised as §f" + profile.name()));
        return true;
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
