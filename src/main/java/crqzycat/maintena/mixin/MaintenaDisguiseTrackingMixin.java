package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a disguise is applied or removed, the real player entity is hidden from everybody else
 * for a moment (DisguiseManager.isHiddenFromOthers). When it becomes visible again, clients
 * receive a fresh spawn packet - with the new identity, or rewritten into the morph's entity type
 * (see DisguisePackets).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MaintenaDisguiseTrackingMixin {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void maintena$hideWhileResending(ServerPlayer viewer, CallbackInfo ci) {
        if (this.entity instanceof ServerPlayer target
                && target != viewer
                && DisguiseManager.getInstance().isHiddenFromOthers(target)) {

            this.removePlayer(viewer);
            ci.cancel();
        }
    }
}
