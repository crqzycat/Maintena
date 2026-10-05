package crqzycat.maintena.event;

import crqzycat.maintena.disguise.DisguiseManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Cleans disguises up when players disconnect. */
public final class DisguiseHandler {
    private DisguiseHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            DisguiseManager.getInstance().undisguise(player);
        });
    }
}
