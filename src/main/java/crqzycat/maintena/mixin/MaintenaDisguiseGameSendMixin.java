package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguisePackets;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same hook as MaintenaDisguiseSendMixin, for versions where ServerGamePacketListenerImpl
 * declares its own send(Packet). If it doesn't, this injector is skipped (require = 0).
 * Running both is harmless: a packet that was already rewritten is never rewritten twice.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MaintenaDisguiseGameSendMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$morphPackets(Packet<?> packet, CallbackInfo ci) {
        if (DisguisePackets.intercept((ServerGamePacketListenerImpl) (Object) this, packet)) {
            ci.cancel();
        }
    }
}
