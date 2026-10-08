package crqzycat.maintena.mixin;

import crqzycat.maintena.freeze.FreezeManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Verwirft die Positionspakete eingefrorener Spieler, damit sich ihre Position auf dem Server nie
 * ändert. Pakete, die nur die Blickrichtung enthalten, laufen normal durch (umschauen bleibt
 * möglich). Zurückgesetzt wird der Spieler vom FreezeManager im nächsten Tick.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MaintenaFreezeMoveMixin {

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void maintena$freezeMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).getPlayer();
        FreezeManager manager = FreezeManager.getInstance();

        if (!manager.isFrozen(player)) {
            return;
        }

        double x = packet.getX(Double.NaN);

        if (Double.isNaN(x)) {
            return; // nur Blickrichtung bzw. Boden-Status: erlaubt
        }

        manager.onMoveAttempt(player, x, packet.getY(Double.NaN), packet.getZ(Double.NaN));
        ci.cancel();
    }
}
