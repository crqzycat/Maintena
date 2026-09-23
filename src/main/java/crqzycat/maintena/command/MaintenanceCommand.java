package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import crqzycat.maintena.maintenance.MaintenanceManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class MaintenanceCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> maintenance = 
            LiteralArgumentBuilder.literal("maintenance")
                .requires(source -> source.hasPermissionLevel(4));
        
        // /maintenance on [minutes]
        maintenance.then(
            LiteralArgumentBuilder.literal("on")
                .then(
                    RequiredArgumentBuilder.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(ctx -> executeEnable(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))
                )
                .executes(ctx -> executeEnable(ctx, 0))
        );
        
        // /maintenance off
        maintenance.then(
            LiteralArgumentBuilder.literal("off")
                .executes(ctx -> executeDisable(ctx))
        );
        
        // /maintenance add <player>
        maintenance.then(
            LiteralArgumentBuilder.literal("add")
                .then(
                    RequiredArgumentBuilder.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeAdd(ctx, StringArgumentType.getString(ctx, "player")))
                )
        );
        
        // /maintenance remove <player>
        maintenance.then(
            LiteralArgumentBuilder.literal("remove")
                .then(
                    RequiredArgumentBuilder.argument("player", StringArgumentType.word())
                        .executes(ctx -> executeRemove(ctx, StringArgumentType.getString(ctx, "player")))
                )
        );
        
        // /maintenance status
        maintenance.then(
            LiteralArgumentBuilder.literal("status")
                .executes(ctx -> executeStatus(ctx))
        );
        
        dispatcher.register(maintenance);
    }
    
    private static int executeEnable(CommandContext<ServerCommandSource> ctx, int minutes) {
        ServerCommandSource source = ctx.getSource();
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        manager.enable(minutes);
        
        if (minutes > 0) {
            source.sendFeedback(() -> Text.of("§a✓ Maintenance enabled for " + minutes + " minutes"), true);
        } else {
            source.sendFeedback(() -> Text.of("§a✓ Maintenance enabled (manual stop)"), true);
        }
        
        return 1;
    }
    
    private static int executeDisable(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        manager.disable();
        source.sendFeedback(() -> Text.of("§a✓ Maintenance disabled"), true);
        
        return 1;
    }
    
    private static int executeAdd(CommandContext<ServerCommandSource> ctx, String player) {
        ServerCommandSource source = ctx.getSource();
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        manager.addWhitelistedPlayer(player);
        source.sendFeedback(() -> Text.of("§a✓ Added " + player + " to whitelist"), true);
        
        return 1;
    }
    
    private static int executeRemove(CommandContext<ServerCommandSource> ctx, String player) {
        ServerCommandSource source = ctx.getSource();
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        manager.removeWhitelistedPlayer(player);
        source.sendFeedback(() -> Text.of("§a✓ Removed " + player + " from whitelist"), true);
        
        return 1;
    }
    
    private static int executeStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        source.sendFeedback(() -> Text.of(manager.getStatus()), false);
        
        return 1;
    }
}
