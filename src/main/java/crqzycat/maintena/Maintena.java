package crqzycat.maintena;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import crqzycat.maintena.command.MaintenanceCommand;
import crqzycat.maintena.maintenance.MaintenanceManager;

public class Maintena implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            MaintenanceCommand.register(dispatcher)
        );
        
        // Initialize manager on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> 
            MaintenanceManager.getInstance().setServer(server)
        );
    }
}
