package crqzycat.maintena.event;

import crqzycat.maintena.vanish.VanishManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vanish-Events: Tab-Liste beim Join korrigieren und dem Spieler regelmäßig in der
 * Actionbar anzeigen, dass er im Vanish ist.
 */
public class VanishHandler {

    private static int tickCounter = 0;

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            VanishManager manager = VanishManager.getInstance();

            manager.onJoin(player);

            if (manager.isVanished(player)) {
                player.sendSystemMessage(Component.literal("§b✓ You are still vanished"));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < 40) {
                return;
            }

            tickCounter = 0;

            for (ServerPlayer player : VanishManager.getInstance().getOnlineVanished(server)) {
                player.sendSystemMessage(Component.literal("§bYou are vanished"), true);
            }
        });
    }
}
