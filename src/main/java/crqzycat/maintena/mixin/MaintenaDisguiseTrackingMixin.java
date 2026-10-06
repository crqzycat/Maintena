package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Decides per viewer what is sent for disguises:
 * <ul>
 *   <li>a disguised player is not sent to anybody else (they see the disguise entity instead),</li>
 *   <li>the disguise entity is not sent to viewers who must not see its (vanished) owner.</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MaintenaDisguiseTrackingMixin {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void maintena$hideDisguiseParts(ServerPlayer viewer, CallbackInfo ci) {
        DisguiseManager manager = DisguiseManager.getInstance();

        if (this.entity instanceof ServerPlayer target && target != viewer && manager.isDisguised(target)) {
            this.removePlayer(viewer);
            ci.cancel();
            return;
        }

        UUID ownerId = manager.getDisguiseOwner(this.entity);

        if (ownerId == null) {
            return;
        }

        if (ownerId.equals(viewer.getUUID())) {
            return; // the owner sees his own disguise
        }

        ServerPlayer owner = viewer.level().getServer().getPlayerList().getPlayer(ownerId);
        VanishManager vanish = VanishManager.getInstance();

        if (owner != null && vanish.isVanished(owner) && !vanish.canSee(viewer)) {
            this.removePlayer(viewer);
            ci.cancel();
        }
    }
}
