package crqzycat.maintena.disguise;

import com.mojang.authlib.GameProfile;
import crqzycat.maintena.vanish.VanishManager;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player and entity disguises. Both keep the REAL player entity in the world (hitbox, damage,
 * animations and equipment all just work); only what other clients are told about it changes.
 *
 * <p><b>Player disguise</b> (as another account): the player-info entry other clients know for his
 * UUID is replaced by one carrying the target's name and skin, then the entity is re-sent so
 * clients rebuild it with the new identity. A {@link FakePlayer} is only the packet source.
 *
 * <p><b>Entity morph</b> (as a mob, boat, ...): the spawn packet other clients get for the player's
 * own entity id announces the morph's entity type instead (see {@link DisguisePackets}). A
 * template entity of that type, which is never added to the world, supplies the entity data, the
 * hitbox shape and the sounds. The owner keeps moving exactly like a player.
 */
public final class DisguiseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final DisguiseManager INSTANCE = new DisguiseManager();

    /** Ticks between hiding the real entity and showing it again with its new look. */
    private static final int RESHOW_DELAY_TICKS = 2;

    /** Ticks after the first morph before we check that the packet hook is really active. */
    private static final int HOOK_CHECK_TICKS = 100;

    /** What the packet layer, the hitbox and the sound code need to know about a morph. */
    public record Morph(
            Entity template,
            EntityType<?> type,
            EntityDimensions dimensions,
            List<SynchedEntityData.DataValue<?>> extras
    ) {
        public boolean living() {
            return template instanceof net.minecraft.world.entity.LivingEntity;
        }
    }

    private static final class State {
        final UUID owner;

        /** Entity morph: the type (null for player disguises). */
        final EntityType<?> type;

        /** Player disguise: the profile of the target (null for entity morphs). */
        final GameProfile skin;

        /** Player disguise: packet source carrying the fake identity. */
        ServerPlayer packetSource;

        /** Entity morph: the morph data and the entity id it is registered under (-1 = none). */
        Morph morph;
        int morphEntityId = -1;

        int ambientTime = -80;

        /** Player disguise: was a fake "joined the game" shown? (then undisguising shows "left") */
        boolean announced;

        /** Nickname: a player identity with the player's own skin (no fake join/leave messages). */
        boolean nick;

        State(UUID owner, EntityType<?> type, GameProfile skin) {
            this.owner = owner;
            this.type = type;
            this.skin = skin;
        }
    }

    /** Owner UUID -> disguise. */
    private final Map<UUID, State> disguises = new ConcurrentHashMap<>();

    /** Entity id of a morphed player -> morph (read on every outgoing packet, keep it cheap). */
    private final Map<Integer, Morph> morphs = new ConcurrentHashMap<>();

    /** Owner UUID -> server tick at which the real entity is shown again. */
    private final Map<UUID, Integer> reshowAt = new ConcurrentHashMap<>();

    /** Morphed players who are removed from everybody's tab list (they "left the game"). */
    private final Set<UUID> tabHidden = ConcurrentHashMap.newKeySet();

    /** Owner UUID -> the tracker of his entity (hide / show it on purpose). */
    private final Map<UUID, DisguiseTracker> trackers = new ConcurrentHashMap<>();

    private int hookCheckAt;

    private DisguiseManager() {
    }

    public static DisguiseManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::shutdown);
    }

    // ==================== queries ====================

    public boolean isDisguised(ServerPlayer player) {
        State state = disguises.get(player.getUUID());

        return state != null && !state.nick;
    }

    /** Is anybody morphed right now? (fast path for the packet hook) */
    public boolean hasMorphs() {
        return !morphs.isEmpty();
    }

    /** Morph of the player entity with this id, or null. */
    public Morph getMorph(int entityId) {
        return morphs.isEmpty() ? null : morphs.get(entityId);
    }

    /** Is this player morphed into a non-player entity? */
    public boolean isMorphed(ServerPlayer player) {
        return !morphs.isEmpty() && morphs.containsKey(player.getId());
    }

    /** Is this player removed from the tab list because of a morph? */
    public boolean isHiddenFromTab(UUID id) {
        return !tabHidden.isEmpty() && tabHidden.contains(id);
    }

    /** Called by the tracking mixin whenever a player entity gets tracked. */
    public void registerTracker(UUID owner, DisguiseTracker tracker) {
        if (trackers.get(owner) != tracker) {
            trackers.put(owner, tracker);
        }
    }

    /** Must the real player entity be hidden from everybody else right now? */
    public boolean isHiddenFromOthers(ServerPlayer player) {
        return !reshowAt.isEmpty() && reshowAt.containsKey(player.getUUID());
    }

    /** Name of the impersonated player, or null. */
    public String getPlayerDisguiseName(ServerPlayer player) {
        if (disguises.isEmpty()) {
            return null;
        }

        State state = disguises.get(player.getUUID());

        return state == null || state.skin == null || state.nick ? null : state.skin.name();
    }

    /** Name other players see for this player: impersonated name or nickname, or null. */
    public String getIdentityName(ServerPlayer player) {
        if (disguises.isEmpty()) {
            return null;
        }

        State state = disguises.get(player.getUUID());

        return state == null || state.skin == null ? null : state.skin.name();
    }

    /** Entity type of an entity morph, or null. */
    public EntityType<?> getDisguiseType(ServerPlayer player) {
        State state = disguises.get(player.getUUID());
        return state == null ? null : state.type;
    }

    /** Owner UUID -> readable description. */
    public Map<UUID, String> describeAll() {
        Map<UUID, String> result = new LinkedHashMap<>();

        for (State state : disguises.values()) {
            if (state.nick) {
                continue;
            }

            result.put(
                    state.owner,
                    state.skin != null
                            ? "player " + state.skin.name()
                            : state.type.getDescription().getString()
            );
        }

        return result;
    }

    // ==================== disguise / undisguise ====================

    public boolean disguiseMob(ServerPlayer player, EntityType<?> type) {
        if (!DisguiseType.isAllowed(type)) {
            return false;
        }

        return start(player, new State(player.getUUID(), type, null));
    }

    /** Disguises the player as another online player. */
    public boolean disguisePlayer(ServerPlayer player, ServerPlayer target) {
        if (player == target) {
            return false;
        }

        return disguiseAsProfile(player, target.getGameProfile());
    }

    /** Disguises the player as any profile. */
    public boolean disguiseAsProfile(ServerPlayer player, GameProfile profile) {
        if (profile.id().equals(player.getUUID())) {
            return false;
        }

        return start(player, new State(player.getUUID(), null, profile));
    }

    public boolean undisguise(ServerPlayer player) {
        if (!disguises.containsKey(player.getUUID())) {
            return false;
        }

        release(player.getUUID(), player);
        return true;
    }

    /** Gives the player a nickname (his own skin under a new name). Replaces a disguise. */
    public boolean nickname(ServerPlayer player, String nick) {
        GameProfile real = player.getGameProfile();
        State state = new State(player.getUUID(), null, new GameProfile(player.getUUID(), nick, real.properties()));
        state.nick = true;

        return start(player, state);
    }

    /** Removes the nickname. @return false if the player has none */
    public boolean clearNickname(ServerPlayer player) {
        State state = disguises.get(player.getUUID());

        if (state == null || !state.nick) {
            return false;
        }

        release(player.getUUID(), player);
        return true;
    }

    /** Current nickname of the player, or null. */
    public String getNickname(ServerPlayer player) {
        State state = disguises.get(player.getUUID());

        return state != null && state.nick ? state.skin.name() : null;
    }

    /** Called when a player disconnects. */
    public void onDisconnect(ServerPlayer player) {
        release(player.getUUID(), null);
        trackers.remove(player.getUUID());
        tabHidden.remove(player.getUUID());
    }

    /** Called when a player joins. */
    public void onJoin(ServerPlayer joined) {

        // Safety net: invisibility left over from a crash with the old (invisibility based) morph
        if (!disguises.containsKey(joined.getUUID())
                && joined.isInvisible()
                && !joined.hasEffect(MobEffects.INVISIBILITY)) {

            joined.setInvisible(false);
        }

        MinecraftServer server = joined.level().getServer();

        // Vanilla sends the complete tab list on join: remove the morphed players again
        for (UUID hiddenId : tabHidden) {
            ServerPlayer hidden = server.getPlayerList().getPlayer(hiddenId);

            if (hidden != null && hidden != joined && mayKnow(hidden, joined)) {
                joined.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(hiddenId)));
            }
        }

        // New viewers must learn the fake identities (entity morphs need nothing: the spawn
        // packet is rewritten on its way out).
        for (State state : disguises.values()) {

            if (state.packetSource == null || state.owner.equals(joined.getUUID())) {
                continue;
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);

            if (owner != null && !mayKnow(owner, joined)) {
                continue;
            }

            sendIdentityTo(joined, state.owner, state.packetSource);
        }
    }

    public void shutdown(MinecraftServer server) {
        for (State state : List.copyOf(disguises.values())) {
            state.packetSource = null;
        }

        disguises.clear();
        morphs.clear();
        reshowAt.clear();
        tabHidden.clear();
        trackers.clear();
    }

    // ==================== internals ====================

    private boolean start(ServerPlayer player, State state) {

        // A previous disguise is torn down silently; the new one re-sends the entity once.
        State old = disguises.remove(state.owner);

        if (old != null) {
            // morph -> morph keeps the player "gone" from tab list / locator bar: no join/leave spam
            teardown(old, player, state.type != null);
        }

        // Register before anything is sent so the packet hook already knows the morph.
        disguises.put(state.owner, state);

        boolean ok;

        try {
            ok = state.skin != null
                    ? applyIdentity(state, player)
                    : applyMorph(state, player);

        } catch (RuntimeException e) {

            LOGGER.error("[Disguise] Could not start disguise for {}", player.getName().getString(), e);
            ok = false;
        }

        if (!ok) {
            disguises.remove(state.owner);
            teardown(state, player, false);
            reshow(player);
            return false;
        }

        return true;
    }

    /** Entity morph. */
    private boolean applyMorph(State state, ServerPlayer player) {

        ServerLevel level = (ServerLevel) player.level();
        Entity template = state.type.create(level, EntitySpawnReason.COMMAND);

        if (template == null) {
            return false;
        }

        // The template is never added to the world, it only supplies data, shape and sounds.
        template.setSilent(true);

        if (template instanceof Mob mob) {
            mob.setNoAi(true);
        }

        // A TNT / fuse based entity would disappear on the client when the fuse runs out.
        if (template instanceof PrimedTnt tnt) {
            tnt.setFuse(Integer.MAX_VALUE);
        }

        List<SynchedEntityData.DataValue<?>> extras = new ArrayList<>();
        List<SynchedEntityData.DataValue<?>> values = template.getEntityData().getNonDefaultValues();

        if (values != null) {
            for (SynchedEntityData.DataValue<?> value : values) {
                if (value.id() != 0) { // id 0 = shared flags, those come from the real player
                    extras.add(value);
                }
            }
        }

        state.morph = new Morph(template, state.type, template.getDimensions(Pose.STANDING), List.copyOf(extras));
        state.morphEntityId = player.getId();
        state.ambientTime = -80;

        morphs.put(state.morphEntityId, state.morph);

        player.refreshDimensions(); // hitbox of the morph, see MaintenaDisguiseDimensionsMixin
        hideFromTab(player);        // a mob is not a player: "left the game", no tab entry
        refreshWaypoint(player);    // ... and no locator bar entry
        reshow(player);

        if (!DisguisePackets.isHookActive()) {
            hookCheckAt = level.getServer().getTickCount() + HOOK_CHECK_TICKS;
        }

        return true;
    }

    /** Player disguise. */
    private boolean applyIdentity(State state, ServerPlayer player) {

        ServerLevel level = (ServerLevel) player.level();

        /*
         * GameProfile and its PropertyMap are immutable. Never put() into them: build the fake
         * profile with the target's property map (skin + signature) as it is.
         */
        GameProfile identity = new GameProfile(
                player.getUUID(),
                state.skin.name(),
                state.skin.properties()
        );

        // Packet source only. Never add this player to the world.
        state.packetSource = FakePlayer.get(level, identity);

        // Without the real chat session clients can't verify this player's signed chat messages.
        // (If this does not compile in your mappings, delete these 4 lines.)
        RemoteChatSession session = player.getChatSession();

        if (session != null) {
            state.packetSource.setChatSession(session);
        }

        broadcastIdentity(player, state.packetSource);
        reshow(player);

        // The "new" player joins - unless the impersonated account is online right now,
        // then a join message would make no sense.
        if (!state.nick && !isOnline(player, state.skin)) {
            state.announced = true;
            announceFake(player, state.skin.name(), true);
        }

        return true;
    }

    private void broadcastIdentity(ServerPlayer subject, ServerPlayer source) {
        MinecraftServer server = subject.level().getServer();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {

            if (viewer == subject || !mayKnow(subject, viewer)) {
                continue;
            }

            sendIdentityTo(viewer, subject.getUUID(), source);
        }
    }

    /** Replaces the player-info entry of {@code subjectId} on one viewer by the one of {@code source}. */
    private static void sendIdentityTo(ServerPlayer viewer, UUID subjectId, ServerPlayer source) {
        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(subjectId)));
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(source)));
    }

    /** Removes everything a disguise changed, without sending the entity again. */
    private void teardown(State state, ServerPlayer player, boolean keepMorphHidden) {

        boolean online = player != null && !player.isRemoved();

        if (state.morphEntityId >= 0) {
            morphs.remove(state.morphEntityId);
            state.morphEntityId = -1;
            state.morph = null;

            if (online) {
                player.refreshDimensions(); // normal hitbox again

                if (!keepMorphHidden) {
                    showInTab(player);      // "joined the game", tab entry back
                    refreshWaypoint(player);
                }
            } else {
                tabHidden.remove(state.owner);
            }
        }

        if (state.packetSource != null) {
            state.packetSource = null;

            if (online) {
                broadcastIdentity(player, player); // real name and skin again

                if (state.announced) {
                    announceFake(player, state.skin.name(), false);
                }
            }

            state.announced = false;
        }
    }

    /** Tab list + chat: the player "left the game" (same messages as vanish). */
    private void hideFromTab(ServerPlayer player) {
        if (tabHidden.add(player.getUUID())) {
            announce(player, false);
        }
    }

    private void showInTab(ServerPlayer player) {
        if (tabHidden.remove(player.getUUID())) {
            announce(player, true);
        }
    }

    private void announce(ServerPlayer player, boolean joined) {
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
    private static boolean isOnline(ServerPlayer self, GameProfile profile) {
        var players = self.level().getServer().getPlayerList();
        ServerPlayer byId = players.getPlayer(profile.id());
        ServerPlayer byName = players.getPlayerByName(profile.name());

        return (byId != null && byId != self) || (byName != null && byName != self);
    }

    /** "Name joined/left the game" for a disguise identity. The tab list is handled by the identity swap. */
    private void announceFake(ServerPlayer player, String name, boolean joined) {
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
    private static void refreshWaypoint(ServerPlayer player) {
        ServerWaypointManager waypoints = ((ServerLevel) player.level()).getWaypointManager();
        WaypointTransmitter transmitter = player;

        waypoints.untrackWaypoint(transmitter);
        waypoints.trackWaypoint(transmitter);
    }

    private void release(UUID owner, ServerPlayer refresh) {

        State state = disguises.remove(owner);

        if (state == null) {
            return;
        }

        boolean online = refresh != null && !refresh.isRemoved();

        teardown(state, online ? refresh : null, false);

        if (!online) {
            reshowAt.remove(owner);
            return;
        }

        reshow(refresh);
    }

    /**
     * Hides the real entity from everybody else and shows it again a moment later, so clients
     * rebuild it with its new (or original) look.
     */
    private void reshow(ServerPlayer player) {
        reshowAt.put(player.getUUID(), player.level().getServer().getTickCount() + RESHOW_DELAY_TICKS);
        hideEntity(player);
    }

    /** Vanished players are unknown to viewers who may not see them. */
    private static boolean mayKnow(ServerPlayer subject, ServerPlayer viewer) {
        VanishManager vanish = VanishManager.getInstance();

        return !(vanish.isVanished(subject) && !vanish.canSee(viewer));
    }

    private void tick(MinecraftServer server) {

        if (!reshowAt.isEmpty()) {
            tickReshow(server);
        }

        if (hookCheckAt != 0 && server.getTickCount() >= hookCheckAt) {
            hookCheckAt = 0;

            if (!DisguisePackets.isHookActive()) {
                LOGGER.error("[Disguise] The packet hook is not active: entity morphs will not be visible. "
                        + "send(Packet) was not found in ServerCommonPacketListenerImpl / "
                        + "ServerGamePacketListenerImpl - check the mixin log.");
            }
        }

        if (disguises.isEmpty()) {
            return;
        }

        for (State state : List.copyOf(disguises.values())) {

            try {
                tickState(state, server);

            } catch (RuntimeException e) {

                LOGGER.error("[Disguise] Error while ticking a disguise, removing it", e);

                disguises.remove(state.owner);
                reshowAt.remove(state.owner);

                if (state.morphEntityId >= 0) {
                    morphs.remove(state.morphEntityId);
                }
            }
        }
    }

    private void tickReshow(MinecraftServer server) {

        int now = server.getTickCount();

        for (Map.Entry<UUID, Integer> entry : List.copyOf(reshowAt.entrySet())) {

            if (now < entry.getValue()) {
                continue;
            }

            reshowAt.remove(entry.getKey());

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (player != null && !player.isRemoved()) {
                showEntity(player);
            }
        }
    }

    private void tickState(State state, MinecraftServer server) {

        ServerPlayer player = server.getPlayerList().getPlayer(state.owner);

        if (player == null || player.isRemoved()) {
            release(state.owner, null);
            return;
        }

        // A respawn creates a new player entity: the disguise ends with the death.
        if (!player.isAlive()) {
            release(state.owner, player);
            return;
        }

        // Idle sounds of the morph (same rhythm as a vanilla mob).
        Morph morph = state.morph;

        if (morph != null
                && morph.template() instanceof Mob
                && !VanishManager.getInstance().isVanished(player)
                && player.getRandom().nextInt(1000) < state.ambientTime++) {

            state.ambientTime = -80;

            SoundEvent sound = DisguiseSounds.ambient(morph.template());

            if (sound != null) {
                ((ServerLevel) player.level()).playSound(
                        (Player) null, player.getX(), player.getY(), player.getZ(),
                        sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
    }

    /** Removes the player entity from every viewer (its own tracker, not dependent on anybody moving). */
    private void hideEntity(ServerPlayer player) {
        DisguiseTracker tracker = trackers.get(player.getUUID());

        if (tracker != null) {
            tracker.maintena$hide(List.copyOf(((ServerLevel) player.level()).players()));
        } else {
            refreshTracking(player);
        }
    }

    /** Lets every viewer in range receive a fresh spawn of the player entity. */
    private void showEntity(ServerPlayer player) {
        DisguiseTracker tracker = trackers.get(player.getUUID());

        if (tracker != null) {
            tracker.maintena$show(List.copyOf(((ServerLevel) player.level()).players()));
        } else {
            refreshTracking(player);
        }
    }

    /** Fallback if no tracker is known: re-evaluates tracking the vanilla way. */
    private static void refreshTracking(ServerPlayer player) {
        ((ServerLevel) player.level()).getChunkSource().move(player);
    }
}
