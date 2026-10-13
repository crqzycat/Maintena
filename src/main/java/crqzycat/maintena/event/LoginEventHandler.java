package crqzycat.maintena.event;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;

/**
 * Sicherheitsnetz für den Wartungsmodus. Die eigentliche Prüfung passiert schon beim Login
 * (MaintenaPlayerListMixin.canPlayerLogin), damit der Spieler gar nicht erst beitritt. Dieser
 * Handler fängt nur noch den Sonderfall ab, dass die Wartung genau zwischen Login und Join
 * eingeschaltet wurde.
 */
public class LoginEventHandler {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MaintenanceManager manager =
                    MaintenanceManager.getInstance();

            if (!manager.isEnabled()) {
                return;
            }

            NameAndId player = handler.getPlayer().nameAndId();

            boolean isOp = server.getPlayerList().isOp(player);

            if (!isOp && !manager.isWhitelisted(player.id(), player.name())) {
                String motd = manager.getMaintenanceMotd();

                if (motd != null) {
                    handler.getPlayer().connection.disconnect(Component.literal(motd));
                }
            }
        });
    }
}
