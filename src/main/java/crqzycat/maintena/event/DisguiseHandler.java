package crqzycat.maintena.event;

import crqzycat.maintena.disguise.DisguiseManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Keeps disguises in sync with joining and leaving players. */
public final class DisguiseHandler {
    private DisguiseHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                DisguiseManager.getInstance().onJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DisguiseManager.getInstance().onDisconnect(handler.getPlayer()));
    }
}
