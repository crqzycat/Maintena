package crqzycat.maintena.mixin;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ersetzt den MOTD in der Server-Liste (Multiplayer-Screen) durch den
 * Wartungsstatus, solange Maintena aktiv ist. Wird bei jeder Server-List-Anfrage
 * (jedem "Ping") neu ausgewertet, damit die Restzeit live mitzählt.
 */
@Mixin(MinecraftServer.class)
public abstract class MaintenaServerStatusMixin {

    @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
    private void maintena$overrideMotd(CallbackInfoReturnable<ServerStatus> cir) {
        String maintenanceMotd = MaintenanceManager.getInstance().getMaintenanceMotd();

        if (maintenanceMotd == null) {
            // Keine Wartung aktiv -> normalen MOTD unangetastet lassen
            return;
        }

        ServerStatus original = cir.getReturnValue();

        if (original == null) {
            return;
        }

        Component newDescription = Component.literal(maintenanceMotd);

        cir.setReturnValue(new ServerStatus(
                newDescription,
                original.players(),
                original.version(),
                original.favicon(),
                original.enforcesSecureChat()
        ));
    }
}
