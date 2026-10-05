package crqzycat.maintena.vanish;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanish: Team-Mitglieder werden für normale Spieler unsichtbar (Spielwelt + Tab-Liste).
 * Sehen dürfen vanished Spieler alle mit Gamemaster-Rechten (OP), alle anderen nicht.
 *
 * Der Zustand liegt nur im Arbeitsspeicher: er bleibt beim Re-Join erhalten, geht aber bei
 * einem Server-Neustart verloren. Das Verstecken in der Spielwelt übernimmt
 * MaintenaTrackedEntityMixin, die fehlenden Kollisionen MaintenaCollisionMixin.
 * Items einsammeln funktioniert normal.
 */
public class VanishManager {

    private static VanishManager instance;

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    private VanishManager() {
    }

    public static synchronized VanishManager getInstance() {
        if (instance == null) {
            instance = new VanishManager();
        }

        return instance;
    }

    public boolean isVanished(ServerPlayer player) {
        return vanished.contains(player.nameAndId().id());
    }

    /** Ist diese Entity ein Spieler im Vanish? (für Mixins, die nur eine Entity haben) */
    public boolean isVanishedEntity(Entity entity) {
        return entity instanceof ServerPlayer player && isVanished(player);
    }

    /** Darf dieser Spieler vanished Spieler sehen? (Gamemaster/OP) */
    public boolean canSee(ServerPlayer viewer) {
        return viewer.createCommandSourceStack().permissions()
                .hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Alle aktuell online befindlichen Spieler im Vanish. */
    public List<ServerPlayer> getOnlineVanished(MinecraftServer server) {
        List<ServerPlayer> result = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isVanished(player)) {
                result.add(player);
            }
        }

        return result;
    }

    /**
     * Schaltet Vanish für einen Spieler um.
     *
     * @return true, wenn sich etwas geändert hat
     */
    public boolean setVanished(ServerPlayer player, boolean value) {
        UUID id = player.nameAndId().id();
        boolean changed = value ? vanished.add(id) : vanished.remove(id);

        if (!changed) {
            return false;
        }

        // Spielwelt: Tracking neu aufbauen, das Mixin entscheidet pro Betrachter
        refreshTracking(player);

        // Tab-Liste: für alle, die den Spieler nicht sehen dürfen, entfernen bzw. wieder eintragen
        MinecraftServer server = player.level().getServer();

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player || canSee(other)) {
                continue;
            }

            if (value) {
                other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(id)));
            } else {
                other.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
            }
        }

        return true;
    }

    /**
     * Nach dem Join: Das Vanilla-Join schickt die komplette Tab-Liste mit, deshalb hier
     * vanished Spieler wieder austragen (für den Neuen bzw. für alle anderen, wenn der
     * Neue selbst im Vanish ist).
     */
    public void onJoin(ServerPlayer joined) {
        MinecraftServer server = joined.level().getServer();

        if (isVanished(joined)) {
            UUID id = joined.nameAndId().id();

            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != joined && !canSee(other)) {
                    other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(id)));
                }
            }
        }

        if (!canSee(joined)) {
            for (ServerPlayer hidden : getOnlineVanished(server)) {
                if (hidden != joined) {
                    joined.connection.send(
                            new ClientboundPlayerInfoRemovePacket(List.of(hidden.nameAndId().id())));
                }
            }
        }
    }

    /**
     * Baut das Entity-Tracking des Spielers neu auf, damit sich die Sichtbarkeit sofort
     * ändert (nicht erst, wenn jemand einen Chunk-Abschnitt wechselt).
     */
    private void refreshTracking(ServerPlayer player) {
        ServerChunkCache chunks = ((ServerLevel) player.level()).getChunkSource();
        chunks.removeEntity(player);
        chunks.addEntity(player);
    }
}
