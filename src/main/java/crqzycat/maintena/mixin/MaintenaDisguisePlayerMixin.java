package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the disguised player's display name use the impersonated player's name. */
@Mixin(Player.class)
public abstract class MaintenaDisguisePlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void maintena$disguiseDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;

        String name = DisguiseManager.getInstance().getPlayerDisguiseName(player);
        if (name != null) cir.setReturnValue(Component.literal(name));
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void maintena$disguiseName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;

        String name = DisguiseManager.getInstance().getPlayerDisguiseName(player);
        if (name != null) cir.setReturnValue(Component.literal(name));
    }
}
