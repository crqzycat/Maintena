package crqzycat.maintena.mixin;

import crqzycat.maintena.ban.BanManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * Ersetzt die Vanilla-Ablehnung beim Login eines gebannten Spielers durch den
 * Maintena-Ban-Screen (Grund, Ersteller, Ablaufdatum und sekundengenaue Restzeit).
 */
@Mixin(PlayerList.class)
public abstract class MaintenaPlayerListMixin {

    @Shadow
    public abstract UserBanList getBans();

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void maintena$customBanScreen(
            SocketAddress address,
            NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir
    ) {
        if (cir.getReturnValue() == null) {
            return;
        }

        UserBanList bans = this.getBans();

        if (!bans.isBanned(nameAndId)) {
            return;
        }

        UserBanListEntry entry = bans.get(nameAndId);

        if (entry == null) {
            return;
        }

        cir.setReturnValue(BanManager.getInstance().buildBanScreen(entry));
    }
}
