package crqzycat.maintena.event;

import crqzycat.maintena.freeze.FreezeManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Freeze-Events: sperrt Interaktionen (Blöcke abbauen/benutzen, Items, Entities), optional den
 * Chat und Schaden für eingefrorene Spieler, setzt Bewegungsversuche zurück und erinnert den
 * Spieler in der Actionbar. Die Bewegung selbst blockt MaintenaFreezeMoveMixin.
 */
public class FreezeHandler {

    private static int tickCounter = 0;

    public static void register() {
        FreezeManager manager = FreezeManager.getInstance();

        // Interaktionen
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> deny(player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> deny(player));
        UseItemCallback.EVENT.register((player, world, hand) -> deny(player));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> deny(player));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> deny(player));

        // Chat (optional): gleiche Nachrichten wie beim Mute
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> allowChat(sender));

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer player = source.getPlayer();
            return player == null || allowChat(player);
        });

        // Schaden (optional)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player)
                        || !manager.getConfig().invulnerable
                        || !manager.isFrozen(player));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> manager.onDisconnect(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            manager.tick(server);

            if (++tickCounter >= 40) {
                tickCounter = 0;
                manager.sendReminders(server);
            }
        });
    }

    private static InteractionResult deny(Player player) {
        return player instanceof ServerPlayer serverPlayer && FreezeManager.getInstance().isFrozen(serverPlayer)
                ? InteractionResult.FAIL
                : InteractionResult.PASS;
    }

    private static boolean allowChat(ServerPlayer player) {
        FreezeManager manager = FreezeManager.getInstance();

        if (!manager.getConfig().blockChat || !manager.isFrozen(player)) {
            return true;
        }

        player.sendSystemMessage(Component.literal(manager.getConfig().deniedChat));
        return false;
    }
}
