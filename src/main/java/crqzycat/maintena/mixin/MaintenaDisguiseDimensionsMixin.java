package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitbox of the morph. Server-only mods can't tell the owner's client about a new hitbox, so the
 * box is only ever SHRUNK to fit the morph (never enlarged): a chicken has a chicken-sized
 * hitbox, but a boat or a golem can't make the owner's movement collide where his client lets
 * him walk. Eye height stays the player's own. {@code require = 0}: without a matching method
 * the hitbox simply isn't adjusted.
 */
@Mixin(Player.class)
public abstract class MaintenaDisguiseDimensionsMixin {

    @Inject(method = {"getDefaultDimensions", "getDimensions"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private void maintena$morphHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }

        DisguiseManager.Morph morph = DisguiseManager.getInstance().getMorph(player.getId());

        if (morph == null) {
            return;
        }

        EntityDimensions original = cir.getReturnValue();
        float width = Math.min(original.width(), morph.dimensions().width());
        float height = Math.min(original.height(), morph.dimensions().height());

        if (width == original.width() && height == original.height()) {
            return;
        }

        cir.setReturnValue(EntityDimensions.scalable(width, height).withEyeHeight(original.eyeHeight()));
    }
}
