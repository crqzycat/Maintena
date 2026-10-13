package crqzycat.maintena.mixin;

import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Passt die Antwort auf die Server-Liste (Multiplayer-Screen) an:
 * <ul>
 *   <li>Ersetzt den MOTD durch den Wartungsstatus, solange Maintena aktiv ist. Wird bei jeder
 *       Anfrage (jedem "Ping") neu ausgewertet, damit die Restzeit live mitzählt.</li>
 *   <li>Rechnet Spieler im Vanish aus der Spielerzahl und der Spielerliste heraus.</li>
 * </ul>
 */
@Mixin(MinecraftServer.class)
public abstract class MaintenaServerStatusMixin {

    @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
    private void maintena$overrideStatus(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus original = cir.getReturnValue();

        if (original == null) {
            return;
        }

        String maintenanceMotd = MaintenanceManager.getInstance().getMaintenanceMotd();
        Set<UUID> hidden = VanishManager.getInstance().getOnlineVanishedIds();

        if (maintenanceMotd == null && hidden.isEmpty()) {
            // Weder Wartung noch Vanish -> Antwort unangetastet lassen
            return;
        }

        Component description = maintenanceMotd != null
                ? Component.literal(maintenanceMotd)
                : original.description();

        Optional<ServerStatus.Players> players = original.players();

        if (players.isPresent() && !hidden.isEmpty()) {
            players = Optional.of(maintena$withoutVanished(players.get(), hidden));
        }

        cir.setReturnValue(new ServerStatus(
                description,
                players,
                original.version(),
                original.favicon(),
                original.enforcesSecureChat()
        ));
    }

    private static ServerStatus.Players maintena$withoutVanished(ServerStatus.Players players, Set<UUID> hidden) {
        List<NameAndId> sample = new ArrayList<>();

        for (NameAndId entry : players.sample()) {
            if (!hidden.contains(entry.id())) {
                sample.add(entry);
            }
        }

        return new ServerStatus.Players(
                players.max(),
                Math.max(0, players.online() - hidden.size()),
                sample
        );
    }
}
