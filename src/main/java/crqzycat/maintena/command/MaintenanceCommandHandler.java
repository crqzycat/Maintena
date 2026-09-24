package crqzycat.maintena.command;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

public class MaintenanceCommandHandler {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerMaintenanceCommand(dispatcher);
        });
    }

    private static void registerMaintenanceCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("maintenance")
                // Falls 'hasPermission' rot bleibt, passe die Methode an (z. B. source.hasPermission(4))
                .requires(source -> source.checkPermission(4)) // Permission level 4 (admin)
                .then(Commands.literal("on")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                    MaintenanceManager.getInstance().enable(minutes);
                                    if (minutes > 0) {
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a✓ Maintenance enabled for " + minutes + " minutes"), true);
                                    } else {
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a✓ Maintenance enabled (manual stop)"), true);
                                    }
                                    return 1;
                                }))
                        .executes(ctx -> {
                            MaintenanceManager.getInstance().enable(0);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a✓ Maintenance enabled (manual stop)"), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            MaintenanceManager.getInstance().disable();
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a✓ Maintenance disabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String player = StringArgumentType.getString(ctx, "player");
                                    MaintenanceManager.getInstance().addWhitelistedPlayer(player);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a✓ Added " + player + " to whitelist"), true);
                                    return 1;
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String player = StringArgumentType.getString(ctx, "player");
                                    MaintenanceManager.getInstance().removeWhitelistedPlayer(player);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a✓ Removed " + player + " from whitelist"), true);
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            String status = MaintenanceManager.getInstance().getStatus();
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal(status), false);
                            return 1;
                        })));
    }
}