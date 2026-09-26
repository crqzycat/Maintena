package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public class MaintenanceCommandHandler {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(buildMaintenanceCommand());
                }
        );
    }

    /**
     * Baut den kompletten "maintenance"-Befehlsbaum. Wird sowohl für den
     * eigenständigen /maintenance Befehl verwendet, als auch von
     * MaintenaCommandHandler, um denselben Baum unter /maintena maintenance
     * verfügbar zu machen.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildMaintenanceCommand() {
        return
                Commands.literal("maintenance")
                        .requires(source -> source.permissions()
                                .hasPermission(
                                        Permissions.COMMANDS_GAMEMASTER))

                        .then(Commands.literal("on")
                                .then(Commands.argument(
                                                        "minutes",
                                                        IntegerArgumentType.integer(0)
                                                )
                                                .executes(ctx -> {
                                                    int minutes =
                                                            IntegerArgumentType.getInteger(
                                                                    ctx,
                                                                    "minutes"
                                                            );

                                                    MaintenanceManager.getInstance()
                                                            .enable(minutes);

                                                    if (minutes > 0) {
                                                        ctx.getSource().sendSuccess(
                                                                () -> Component.literal(
                                                                        "§a✓ Maintenance enabled for "
                                                                                + minutes
                                                                                + " minutes"
                                                                ),
                                                                true
                                                        );
                                                    } else {
                                                        ctx.getSource().sendSuccess(
                                                                () -> Component.literal(
                                                                        "§a✓ Maintenance enabled (manual stop)"
                                                                ),
                                                                true
                                                        );
                                                    }

                                                    return 1;
                                                })
                                )
                                .executes(ctx -> {
                                    MaintenanceManager.getInstance()
                                            .enable(0);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "§a✓ Maintenance enabled (manual stop)"
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                        )

                        .then(Commands.literal("off")
                                .executes(ctx -> {
                                    MaintenanceManager.getInstance()
                                            .disable();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "§a✓ Maintenance disabled"
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                        )

                        .then(Commands.literal("add")
                                .then(Commands.argument(
                                                        "player",
                                                        StringArgumentType.word()
                                                )
                                                .suggests((context, builder) -> {
                                                    // Alle Spieler suggerieren, außer die bereits auf der Whitelist stehen
                                                    MaintenanceManager manager =
                                                            MaintenanceManager.getInstance();

                                                    java.util.Collection<String> candidates =
                                                            new java.util.ArrayList<>(
                                                                    manager.getAllPlayerNames()
                                                            );
                                                    candidates.removeAll(
                                                            manager.getWhitelistedPlayers()
                                                    );

                                                    return SharedSuggestionProvider.suggest(
                                                            candidates,
                                                            builder
                                                    );
                                                })
                                                .executes(ctx -> {
                                                    String player =
                                                            StringArgumentType.getString(
                                                                    ctx,
                                                                    "player"
                                                            );

                                                    MaintenanceManager.getInstance()
                                                            .addWhitelistedPlayer(player);

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    "§a✓ Added "
                                                                            + player
                                                                            + " to whitelist"
                                                            ),
                                                            true
                                                    );

                                                    return 1;
                                                })
                                )
                        )

                        .then(Commands.literal("remove")
                                .then(Commands.argument(
                                                        "player",
                                                        StringArgumentType.word()
                                                )
                                                .suggests((context, builder) -> {
                                                    // Nur Spieler auf Whitelist suggerieren
                                                    return SharedSuggestionProvider.suggest(
                                                            MaintenanceManager.getInstance()
                                                                    .getWhitelistedPlayers(),
                                                            builder
                                                    );
                                                })
                                                .executes(ctx -> {
                                                    String player =
                                                            StringArgumentType.getString(
                                                                    ctx,
                                                                    "player"
                                                            );

                                                    MaintenanceManager.getInstance()
                                                            .removeWhitelistedPlayer(player);

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    "§a✓ Removed "
                                                                            + player
                                                                            + " from whitelist"
                                                            ),
                                                            true
                                                    );

                                                    return 1;
                                                })
                                )
                        )

                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    String status =
                                            MaintenanceManager.getInstance()
                                                    .getStatus();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(status),
                                            false
                                    );

                                    return 1;
                                })
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    MaintenanceManager.getInstance().clearWhitelist();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "§a✓ Whitelist cleared"
                                            ),
                                            true
                                    );

                                    return 1;
                                })
                        );
    }
}