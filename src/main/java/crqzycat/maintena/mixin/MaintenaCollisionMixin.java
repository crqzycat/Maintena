package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanished Spieler haben keine Entity-Kollisionen: Sie schieben keine Mobs/Entities weg und
 * werden auch nicht von ihnen weggeschoben. Items einsammeln funktioniert weiterhin.
 *
 * Disguise-Entities (Mob/Fake-Spieler) sind ebenfalls kollisionsfrei.
 *
 * Gilt auch für Boote (rufen Entity.push per super auf). Loren überschreiben push ohne super
 * und werden deshalb in MaintenaMinecartCollisionMixin behandelt.
 */
@Mixin(Entity.class)
public abstract class MaintenaCollisionMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void maintena$noPushWhileVanished(Entity other, CallbackInfo ci) {
        VanishManager manager = VanishManager.getInstance();
        DisguiseManager disguises = DisguiseManager.getInstance();

        if (manager.isVanishedEntity((Entity) (Object) this) || manager.isVanishedEntity(other)
                || disguises.isDisguiseEntity((Entity) (Object) this) || disguises.isDisguiseEntity(other)) {
            ci.cancel();
        }
    }
}
