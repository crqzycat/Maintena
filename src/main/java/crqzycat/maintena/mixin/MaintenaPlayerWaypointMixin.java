package crqzycat.maintena.mixin;

import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Falls ServerPlayer makeWaypointConnectionWith selbst überschreibt (ohne super), greift
 * MaintenaWaypointMixin dort nicht. Diese Variante deckt das ab und ist optional (require = 0):
 * Gibt es die Methode in ServerPlayer nicht, wird sie einfach übersprungen.
 */
@Mixin(ServerPlayer.class)
public abstract class MaintenaPlayerWaypointMixin {

    @Inject(method = "makeWaypointConnectionWith", at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$hideVanishedWaypoint(ServerPlayer receiver, CallbackInfoReturnable<Optional<?>> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        VanishManager manager = VanishManager.getInstance();

        if (manager.isVanished(self) && !manager.canSee(receiver)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
