package crqzycat.maintena;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.command.MaintenanceCommandHandler;
import crqzycat.maintena.event.LoginEventHandler;

public class Maintena implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register commands
        MaintenanceCommandHandler.register();
        
        // Register login event handler to kick non-whitelisted players
        LoginEventHandler.register();
        
        // Initialize manager on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MaintenanceManager.getInstance().setServer(server);
        });
    }
}
