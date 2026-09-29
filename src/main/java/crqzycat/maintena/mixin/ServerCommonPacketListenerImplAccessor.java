package crqzycat.maintena.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Öffnet das interne "connection"-Feld von ServerCommonPacketListenerImpl (der
 * Basisklasse von ServerGamePacketListenerImpl), damit IpBanManager an die
 * Netzwerkverbindung eines Spielers kommt, ohne dass PlayerList dafür eine
 * öffentliche Hilfsmethode anbieten müsste.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {

    @Accessor("connection")
    Connection maintena$getConnection();
}
