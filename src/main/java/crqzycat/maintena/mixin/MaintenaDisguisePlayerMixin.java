package crqzycat.maintena.mixin;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.disguise.DisguiseSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side identity of a disguised player:
 * <ul>
 *   <li>player disguise / nickname: chat/display name shows the impersonated name (or the
 *       nickname) with the team formatting of that name (if it is in a team), so the real team
 *       can't give him away. {@code getName()} is deliberately left alone: commands, logs and audit output keep
 *       the real identity.</li>
 *   <li>entity morph: hurt and death sounds are the morph's, not the player's.</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class MaintenaDisguisePlayerMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void maintena$disguiseDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;

        String name = DisguiseManager.getInstance().getIdentityName(player);
        if (name == null) return;

        PlayerTeam team = player.level().getServer().getScoreboard().getPlayersTeam(name);

        cir.setReturnValue(PlayerTeam.formatNameForTeam(team, Component.literal(name)));
    }

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$morphHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (!DisguiseSounds.hasHurt() || !((Object) this instanceof ServerPlayer player)) return;

        DisguiseManager.Morph morph = DisguiseManager.getInstance().getMorph(player.getId());
        if (morph == null) return;

        cir.setReturnValue(DisguiseSounds.hurt(morph.template(), source));
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true, require = 0)
    private void maintena$morphDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!DisguiseSounds.hasDeath() || !((Object) this instanceof ServerPlayer player)) return;

        DisguiseManager.Morph morph = DisguiseManager.getInstance().getMorph(player.getId());
        if (morph == null) return;

        cir.setReturnValue(DisguiseSounds.death(morph.template()));
    }
}
