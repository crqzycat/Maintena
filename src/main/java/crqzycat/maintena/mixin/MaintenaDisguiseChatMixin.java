package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * A player morphed into a non-player entity is removed from everybody's tab list (like a player
 * who left the game). A client that receives a signed chat message from a sender it has no
 * tab entry for disconnects with "Chat message validation failure". So chat from such a player
 * is delivered as a plain system message with the usual chat format instead.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MaintenaDisguiseChatMixin {

    @Inject(method = "sendPlayerChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$chatFromHiddenPlayer(PlayerChatMessage message, ChatType.Bound boundType, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        UUID sender = message.sender();

        if (sender.equals(self.getPlayer().getUUID()) || !DisguiseManager.getInstance().isHiddenFromTab(sender)) {
            return;
        }

        self.send(new ClientboundSystemChatPacket(boundType.decorate(message.decoratedContent()), false));
        ci.cancel();
    }
}
