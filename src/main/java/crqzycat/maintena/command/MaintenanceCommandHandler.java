package crqzycat.maintena.command;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

public class MaintenanceCommandHandler {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerMaintenanceCommand(dispatcher);
        });
    }
    
    private static void registerMaintenanceCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("maintenance")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.literal("on")
                .then(CommandManager.argument("minutes", IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                        MaintenanceManager.getInstance().enable(minutes);
                        if (minutes > 0) {
                            ctx.getSource().sendFeedback(() -> 
                                Text.literal("§a✓ Maintenance enabled for " + minutes + " minutes"), true);
                        } else {
                            ctx.getSource().sendFeedback(() -> 
                                Text.literal("§a✓ Maintenance enabled (manual stop)"), true);
                        }
                        return 1;
                    }))
                .executes(ctx -> {
                    MaintenanceManager.getInstance().enable(0);
                    ctx.getSource().sendFeedback(() -> 
                        Text.literal("§a✓ Maintenance enabled (manual stop)"), true);
                    return 1;
                }))
            .then(CommandManager.literal("off")
                .executes(ctx -> {
                    MaintenanceManager.getInstance().disable();
                    ctx.getSource().sendFeedback(() -> 
                        Text.literal("§a✓ Maintenance disabled"), true);
                    return 1;
                }))
            .then(CommandManager.literal("add")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String player = StringArgumentType.getString(ctx, "player");
                        MaintenanceManager.getInstance().addWhitelistedPlayer(player);
                        ctx.getSource().sendFeedback(() -> 
                            Text.literal("§a✓ Added " + player + " to whitelist"), true);
                        return 1;
                    })))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String player = StringArgumentType.getString(ctx, "player");
                        MaintenanceManager.getInstance().removeWhitelistedPlayer(player);
                        ctx.getSource().sendFeedback(() -> 
                            Text.literal("§a✓ Removed " + player + " from whitelist"), true);
                        return 1;
                    })))
            .then(CommandManager.literal("status")
                .executes(ctx -> {
                    String status = MaintenanceManager.getInstance().getStatus();
                    ctx.getSource().sendFeedback(() -> 
                        Text.literal(status), false);
                    return 1;
                })));
    }
}
