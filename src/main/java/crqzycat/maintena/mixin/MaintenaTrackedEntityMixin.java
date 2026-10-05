package crqzycat.maintena.mixin;

import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Versteckt vanished Spieler in der Spielwelt: Der Server entscheidet pro Betrachter, ob ihm
 * die Entity überhaupt gesendet wird. Für normale Spieler wird das Pairing entfernt und nicht
 * wieder aufgebaut, OPs sehen den Spieler weiterhin.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MaintenaTrackedEntityMixin {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void maintena$hideVanished(ServerPlayer viewer, CallbackInfo ci) {
        if (!(this.entity instanceof ServerPlayer target) || target == viewer) {
            return;
        }

        VanishManager manager = VanishManager.getInstance();

        if (manager.isVanished(target) && !manager.canSee(viewer)) {
            this.removePlayer(viewer);
            ci.cancel();
        }
    }
}
