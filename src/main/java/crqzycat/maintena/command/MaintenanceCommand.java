package crqzycat.maintena.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import crqzycat.maintena.maintenance.MaintenanceManager;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;

public class MaintenanceCommand {
    
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        // This will be called from the event
    }
    
    public static int executeEnable(int minutes) {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        manager.enable(minutes);
        return 1;
    }
    
    public static int executeDisable() {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        manager.disable();
        return 1;
    }
    
    public static int executeAdd(String player) {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        manager.addWhitelistedPlayer(player);
        return 1;
    }
    
    public static int executeRemove(String player) {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        manager.removeWhitelistedPlayer(player);
        return 1;
    }
    
    public static int executeStatus() {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        String status = manager.getStatus();
        return 1;
    }
}
