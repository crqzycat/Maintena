package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.maintenance.MaintenanceManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class MaintenanceCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("maintenance")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("on")
                        .then(CommandManager.argument("minutes", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeEnable(ctx, IntegerArgumentType.getInteger(ctx, "minutes"))))
                        .executes(ctx -> executeEnable(ctx, 0)))
                .then(CommandManager.literal("off")
                        .executes(ctx -> executeDisable(ctx)))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeAdd(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeRemove(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(CommandManager.literal("status")
                        .executes(ctx -> executeStatus(ctx))));
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
