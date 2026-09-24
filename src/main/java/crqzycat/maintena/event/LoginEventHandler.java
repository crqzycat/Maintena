package crqzycat.maintena.event;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.minecraft.network.chat.Component;

public class LoginEventHandler {

    public static void register() {
        ServerLoginConnectionEvents.QUERY_START.register(
                (handler, server, sender, synchronizer) -> {
                    MaintenanceManager manager =
                            MaintenanceManager.getInstance();

                    if (!manager.isEnabled()) {
                        return;
                    }

                    // Prüft den Spielernamen vor dem Login.
                    String playerName = handler.getGameProfile().getName();

                    if (!manager.isWhitelisted(playerName)) {
                        Component kickMessage = Component.literal(
                                manager.getConfig().kickMessage
                        );

                        handler.disconnect(kickMessage);
                    }
                }
        );
    }
}