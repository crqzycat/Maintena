package crqzycat.maintena.event;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.minecraft.text.Text;

public class LoginEventHandler {
    
    public static void register() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            MaintenanceManager manager = MaintenanceManager.getInstance();
            
            // If maintenance mode is enabled and player is not whitelisted, reject login
            if (manager.isEnabled()) {
                String playerName = handler.getProfile().getName();
                
                if (!manager.isWhitelisted(playerName)) {
                    // Disconnect player with kick message
                    Text kickMessage = Text.literal(manager.getConfig().kickMessage);
                    handler.disconnect(kickMessage);
                }
            }
        });
    }
}
