package crqzycat.maintena.mixin;

import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * Login-Prüfungen von Maintena:
 * <ul>
 *   <li>Ersetzt die Vanilla-Ablehnung eines gebannten Spielers (Name- oder IP-Ban) durch den
 *       Maintena-Ban-Screen (Grund, Ersteller, Ablaufdatum und sekundengenaue Restzeit).</li>
 *   <li>Wartungsmodus: Spieler ohne OP und ohne Whitelist-Eintrag (per UUID) werden schon beim
 *       Login abgewiesen, sie betreten den Server also gar nicht erst (keine Join-Meldung,
 *       kein Tab-Eintrag).</li>
 *   <li>Vanish: Join-/Leave-Meldungen von Spielern im Vanish gehen nur an Teammitglieder.</li>
 * </ul>
 */
@Mixin(PlayerList.class)
public abstract class MaintenaPlayerListMixin {

    @Shadow
    public abstract UserBanList getBans();

    @Shadow
    public abstract IpBanList getIpBans();

    @Shadow
    public abstract boolean isOp(NameAndId nameAndId);

    @Shadow
    public abstract List<ServerPlayer> getPlayers();

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void maintena$customBanScreen(
            SocketAddress address,
            NameAndId nameAndId,
            CallbackInfoReturnable<Component> cir
    ) {
        if (cir.getReturnValue() == null) {
            // Vanilla lässt den Spieler rein: jetzt noch den Wartungsmodus prüfen
            MaintenanceManager maintenance = MaintenanceManager.getInstance();

            if (maintenance.isEnabled()
                    && !this.isOp(nameAndId)
                    && !maintenance.isWhitelisted(nameAndId.id(), nameAndId.name())) {
                String motd = maintenance.getMaintenanceMotd();

                if (motd != null) {
                    cir.setReturnValue(Component.literal(motd));
                }
            }

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

    /**
     * Unterdrückt die Vanilla-Meldungen "X joined the game" / "X left the game" für Spieler im
     * Vanish. Teammitglieder (die vanished Spieler sehen dürfen) und die Konsole bekommen sie weiter.
     * require = 0: Sollte sich die Methode in einer MC-Version ändern, startet der Mod trotzdem.
     */
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void maintena$hideVanishedJoinLeave(Component message, boolean overlay, CallbackInfo ci) {
        VanishManager vanish = VanishManager.getInstance();
        ServerPlayer subject = vanish.findVanishedJoinLeaveSubject(message, this.getPlayers());

        if (subject == null) {
            return;
        }

        for (ServerPlayer other : this.getPlayers()) {
            if (vanish.canSee(other)) {
                other.sendSystemMessage(message);
            }
        }

        subject.level().getServer().sendSystemMessage(message);
        ci.cancel();
    }

    private static String maintena$extractIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }

        return null;
    }
}
