package crqzycat.maintena;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import crqzycat.maintena.maintenance.MaintenanceManager;

public class Maintena implements ModInitializer {

    @Override
    public void onInitialize() {
        // Initialize manager on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MaintenanceManager.getInstance().setServer(server);
        });
    }
}
