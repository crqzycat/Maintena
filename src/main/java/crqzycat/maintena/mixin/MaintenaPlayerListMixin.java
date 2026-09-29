package crqzycat.maintena.mixin;

import crqzycat.maintena.ban.BanManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Ersetzt die Vanilla-Ablehnung beim Login eines gebannten Spielers (Name- oder IP-Ban)
 * durch den Maintena-Ban-Screen (Grund, Ersteller, Ablaufdatum und sekundengenaue Restzeit).
 */
@Mixin(PlayerList.class)
public abstract class MaintenaPlayerListMixin {

    @Shadow
    public abstract UserBanList getBans();

    @Shadow
    public abstract IpBanList getIpBans();

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void maintena$customBanScreen(
            SocketAddress address,
            NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir
    ) {
        if (cir.getReturnValue() == null) {
            return;
        }

        UserBanListEntry nameBan = this.getBans().get(nameAndId);

        if (nameBan != null) {
            cir.setReturnValue(BanManager.getInstance().buildBanScreen(nameBan));
            return;
        }

        String ip = maintena$extractIp(address);

        if (ip == null) {
            return;
        }

        IpBanListEntry ipBan = this.getIpBans().get(ip);

        if (ipBan == null) {
            return;
        }

        cir.setReturnValue(BanManager.getInstance().buildBanScreen(
                ipBan.getReason(), ipBan.getSource(), ipBan.getCreated(), ipBan.getExpires()
        ));
    }

    private static String maintena$extractIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }

        return null;
    }
}
