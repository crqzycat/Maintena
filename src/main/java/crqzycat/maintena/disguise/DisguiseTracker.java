package crqzycat.maintena.disguise;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Implemented by ChunkMap$TrackedEntity (via MaintenaDisguiseTrackingMixin). Lets the disguise
 * code hide a player entity from its viewers and show it again on purpose, instead of waiting
 * for viewers to move (vanilla only re-checks who sees an entity when somebody moves).
 */
public interface DisguiseTracker {

    /** Removes the entity from every given viewer (sends the remove packet, forgets the viewer). */
    void maintena$hide(List<ServerPlayer> viewers);

    /** Re-evaluates every given viewer: those in range get a fresh spawn. */
    void maintena$show(List<ServerPlayer> viewers);
}
