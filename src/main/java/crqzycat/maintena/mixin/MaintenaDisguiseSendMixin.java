package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguisePackets;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-viewer packet hook for the entity morph (see DisguisePackets). Every game packet that goes
 * to a player passes here. {@code require = 0}: if the method lives in another class of the
 * hierarchy in the running version, MaintenaDisguiseGameSendMixin covers it.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class MaintenaDisguiseSendMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$morphPackets(Packet<?> packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game
                && DisguisePackets.intercept(game, packet)) {
            ci.cancel();
        }
    }
}
