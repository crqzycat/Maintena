package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.disguise.DisguiseTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * While a disguise is applied or removed, the real player entity is hidden from everybody else
 * for a moment (DisguiseManager.isHiddenFromOthers). When it becomes visible again, clients
 * receive a fresh spawn packet - with the new identity, or rewritten into the morph's entity type
 * (see DisguisePackets).
 *
 * Hiding and showing is driven explicitly through {@link DisguiseTracker}: vanilla only re-checks
 * who sees an entity when a VIEWER moves, which is far too unreliable for a 2-tick window.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MaintenaDisguiseTrackingMixin implements DisguiseTracker {

    @Shadow
    @Final
    Entity entity;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Shadow
    public abstract void updatePlayer(ServerPlayer player);

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void maintena$register(CallbackInfo ci) {
        if (this.entity instanceof ServerPlayer player) {
            DisguiseManager.getInstance().registerTracker(player.getUUID(), this);
        }
    }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void maintena$hideWhileResending(ServerPlayer viewer, CallbackInfo ci) {
        if (this.entity instanceof ServerPlayer target) {

            // Fallback registration in case the constructor hook did not apply
            DisguiseManager.getInstance().registerTracker(target.getUUID(), this);

            if (target != viewer && DisguiseManager.getInstance().isHiddenFromOthers(target)) {
                this.removePlayer(viewer);
                ci.cancel();
            }
        }
    }

    @Override
    public void maintena$hide(List<ServerPlayer> viewers) {
        for (ServerPlayer viewer : viewers) {
            if (viewer != this.entity) {
                this.removePlayer(viewer);
            }
        }
    }

    @Override
    public void maintena$show(List<ServerPlayer> viewers) {
        for (ServerPlayer viewer : viewers) {
            if (viewer != this.entity) {
                this.updatePlayer(viewer);
            }
        }
    }
}
