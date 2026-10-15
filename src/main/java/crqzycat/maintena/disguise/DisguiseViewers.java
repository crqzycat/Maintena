package crqzycat.maintena.disguise;

import com.mojang.authlib.GameProfile;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;

import java.util.List;
import java.util.UUID;

/**
 * Hilfsfunktionen für das, was die anderen Spieler (Viewer) über einen verkleideten Spieler
 * erfahren: Tab-Liste, Join-/Leave-Meldungen, Locator-Bar und Entity-Tracking. Ausgelagert aus dem
 * DisguiseManager; hier gibt es keinen eigenen Zustand, alles sind statische Methoden.
 */
final class DisguiseViewers {

    private DisguiseViewers() {
    }

    /** Replaces the player-info entry of {@code subjectId} on one viewer by the one of {@code source}. */
    static void sendIdentityTo(ServerPlayer viewer, UUID subjectId, ServerPlayer source) {
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(subjectId)));
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(source)));
    }

    static void broadcastIdentity(ServerPlayer subject, ServerPlayer source) {
        MinecraftServer server = subject.level().getServer();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {

            if (viewer == subject || !mayKnow(subject, viewer)) {
                continue;
            }

            sendIdentityTo(viewer, subject.getUUID(), source);
        }
    }

    static void announce(ServerPlayer player, boolean joined) {
        Component message = Component.translatable(
                joined ? "multiplayer.player.joined" : "multiplayer.player.left",
                player.getDisplayName()
        ).withStyle(ChatFormatting.YELLOW);

        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {

            if (other == player || !mayKnow(player, other)) {
                continue;
            }

            if (joined) {
                other.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
            } else {
                other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            }

            other.sendSystemMessage(message);
        }
    }

    /** Is the account behind this profile currently on the server? */
    static boolean isOnline(ServerPlayer self, GameProfile profile) {
        var players = self.level().getServer().getPlayerList();
        ServerPlayer byId = players.getPlayer(profile.id());
        ServerPlayer byName = players.getPlayerByName(profile.name());

        return (byId != null && byId != self) || (byName != null && byName != self);
    }

    /** "Name joined/left the game" for a disguise identity. The tab list is handled by the identity swap. */
    static void announceFake(ServerPlayer player, String name, boolean joined) {
        Component message = Component.translatable(
                joined ? "multiplayer.player.joined" : "multiplayer.player.left",
                Component.literal(name)
        ).withStyle(ChatFormatting.YELLOW);

        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && mayKnow(player, other)) {
                other.sendSystemMessage(message);
            }
        }
    }

    /** Locator bar: rebuild the waypoint, the waypoint mixins decide whether it exists. */
    static void refreshWaypoint(ServerPlayer player) {
        ServerWaypointManager waypoints = ((ServerLevel) player.level()).getWaypointManager();
        WaypointTransmitter transmitter = player;

        waypoints.untrackWaypoint(transmitter);
        waypoints.trackWaypoint(transmitter);
    }

    /** Vanished players are unknown to viewers who may not see them. */
    static boolean mayKnow(ServerPlayer subject, ServerPlayer viewer) {
        VanishManager vanish = VanishManager.getInstance();

        return !(vanish.isVanished(subject) && !vanish.canSee(viewer));
    }

    /** Fallback if no tracker is known: re-evaluates tracking the vanilla way. */
    static void refreshTracking(ServerPlayer player) {
        ((ServerLevel) player.level()).getChunkSource().move(player);
    }
}
