package crqzycat.maintena.event;

import crqzycat.maintena.mute.MuteData;
import crqzycat.maintena.mute.MuteManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Blockiert Chatnachrichten gemuteter Spieler: normaler Chat sowie /msg, /tell, /w, /me, /say,
 * /teammsg (alles, was Fabric als Chat- bzw. Command-Nachricht meldet). Alle anderen Befehle
 * bleiben unberührt. Außerdem räumt ein Sekunden-Tick abgelaufene Mutes auf.
 */
public class ChatMuteHandler {

    private static int tickCounter = 0;

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, sender, params) -> allow(sender)
        );

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer player = source.getPlayer();
            return player == null || allow(player); // Konsole/Commandblock nie blockieren
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                MuteManager.getInstance().tick(server);
            }
        });
    }

    private static boolean allow(ServerPlayer player) {
        MuteManager manager = MuteManager.getInstance();
        MuteData.Entry entry = manager.find(player.nameAndId().id());

        if (entry == null) {
            return true;
        }

        player.sendSystemMessage(manager.buildDeniedMessage(entry));
        return false;
    }
}
