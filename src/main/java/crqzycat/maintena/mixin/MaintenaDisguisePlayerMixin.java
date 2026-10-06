package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat/display name of a disguised player shows the impersonated name (with the player's own team
 * formatting, so prefix/colour don't give him away). {@code getName()} is deliberately left alone:
 * commands, logs and death/audit output keep the real identity.
 */
@Mixin(Player.class)
public abstract class MaintenaDisguisePlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void maintena$disguiseDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;

        String name = DisguiseManager.getInstance().getPlayerDisguiseName(player);
        if (name == null) return;

        cir.setReturnValue(PlayerTeam.formatNameForTeam(player.getTeam(), Component.literal(name)));
    }
}
