package crqzycat.maintena.mixin;

import crqzycat.maintena.gui.SettingsGUIHandler;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fängt die Klicks der Menü-Buttons ab (custom click action "maintena:run"). Alle anderen
 * Custom-Click-Actions bleiben unberührt.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class MaintenaClickActionMixin {

    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void maintena$handleMenuClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!SettingsGUIHandler.ACTION_ID.equals(packet.id().toString())) {
            return;
        }

        if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            SettingsGUIHandler.handleClick(game.getPlayer(), packet.payload().orElse(null));
        }

        ci.cancel();
    }
}
