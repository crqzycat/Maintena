package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Entfernt vanished Spieler von der Locator Bar: Für Betrachter, die vanished Spieler nicht
 * sehen dürfen, entsteht gar keine Waypoint-Verbindung. OPs sehen sie weiterhin.
 */
@Mixin(LivingEntity.class)
public abstract class MaintenaWaypointMixin {

    @Inject(method = "makeWaypointConnectionWith", at = @At("HEAD"), cancellable = true)
    private void maintena$hideVanishedWaypoint(ServerPlayer receiver, CallbackInfoReturnable<Optional<?>> cir) {
        if ((Object) this instanceof ServerPlayer self) {
            VanishManager manager = VanishManager.getInstance();

            // Disguised as a non-player entity: a mob has no locator bar entry
            if (DisguiseManager.getInstance().isMorphed(self)
                    || (manager.isVanished(self) && !manager.canSee(receiver))) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }
}
