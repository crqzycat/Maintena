package crqzycat.maintena.event;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;

public class LoginEventHandler {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MaintenanceManager manager =
                    MaintenanceManager.getInstance();

            if (!manager.isEnabled()) {
                return;
            }

            String playerName = handler.getPlayer()
                    .nameAndId()
                    .name();

            if (!manager.isWhitelisted(playerName)) {
                handler.getPlayer().connection.disconnect(
                        Component.literal(manager.getConfig().kickMessage)
                );
            }
        });
    }
}