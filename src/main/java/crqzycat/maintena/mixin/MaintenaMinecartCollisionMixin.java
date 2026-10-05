package crqzycat.maintena.mixin;

import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loren überschreiben Entity.push ohne super-Aufruf, deshalb braucht es hier eine eigene
 * Sperre: Vanished Spieler schieben keine Loren und werden nicht von ihnen verschoben.
 *
 * Die Klasse liegt je nach Minecraft-Version in einem anderen Paket (vehicle bzw.
 * vehicle.minecart). @Pseudo überspringt den Namen, den es in der laufenden Version nicht gibt.
 */
@Pseudo
@Mixin(targets = {
        "net.minecraft.world.entity.vehicle.minecart.AbstractMinecart",
        "net.minecraft.world.entity.vehicle.AbstractMinecart"
})
public abstract class MaintenaMinecartCollisionMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void maintena$noPushWhileVanished(Entity other, CallbackInfo ci) {
        if (VanishManager.getInstance().isVanishedEntity(other)) {
            ci.cancel();
        }
    }
}
