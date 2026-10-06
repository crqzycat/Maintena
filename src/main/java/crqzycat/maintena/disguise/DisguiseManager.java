package crqzycat.maintena.disguise;

import com.mojang.authlib.GameProfile;
import crqzycat.maintena.vanish.VanishManager;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mob/entity and player disguises. There are two different mechanisms:
 *
 * <p><b>Player disguise</b> (as another account): no extra entity at all. The real player entity stays
 * in the world (so animations, equipment, hitbox and damage all just work); only the identity the
 * other clients know for his UUID is swapped: a hidden player-info entry with the target's name and
 * skin replaces the real one, then the entity is re-sent so clients rebuild it with the new identity.
 * The {@link FakePlayer} is only used as a packet source and is never added to the world.
 *
 * <p><b>Entity disguise</b> (as a mob, boat, ...): a separate entity follows the player. The real
 * player is hidden from everybody else by {@code MaintenaDisguiseTrackingMixin} and set invisible, so
 * the owner sees the disguise instead of his body. The previous invisibility is restored afterwards.
 */
public final class DisguiseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final DisguiseManager INSTANCE = new DisguiseManager();

    /** More than this many rebuilds inside the window means the entity can't be kept alive (e.g. Peaceful). */
    private static final int MAX_REBUILDS = 5;
    private static final int REBUILD_WINDOW_TICKS = 100;

    /** Ticks between "hide the entity" and "show it again with the new identity". */
    private static final int RESHOW_DELAY_TICKS = 2;

    private static final class State {
        final UUID owner;
        /** Entity disguise: the entity type (null for player disguises). */
        final EntityType<?> type;
        /** Player disguise: the profile (name + skin) of the target (null for entity disguises). */
        final GameProfile skin;

        /** Player disguise: packet source carrying the fake identity (never added to the world). */
        ServerPlayer packetSource;
        /** Player disguise: true while the real entity is hidden to re-send it with the new identity. */
        volatile boolean refreshing;

        /** Entity disguise: invisibility of the player before the disguise, restored afterwards. */
        boolean wasInvisible;
        /** Entity disguise: the entity following the player. */
        Entity entity;
        int windowStart;
        int rebuilds;

        State(UUID owner, EntityType<?> type, GameProfile skin) {
            this.owner = owner;
            this.type = type;
            this.skin = skin;
        }
    }

    /** owner UUID -> disguise. Concurrent because mixins may read it from other threads. */
    private final Map<UUID, State> disguises = new ConcurrentHashMap<>();
    /** disguise entity UUID -> owner UUID (entity disguises only). */
    private final Map<UUID, UUID> entityOwners = new ConcurrentHashMap<>();
    /** owner UUID -> server tick at which the real entity is shown to others again. */
    private final Map<UUID, Integer> pendingShow = new ConcurrentHashMap<>();

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
        return !disguises.isEmpty() && disguises.containsKey(player.getUUID());
    }

    /**
     * Must the real player entity be hidden from everybody else right now? True for entity disguises
     * (a separate entity is shown instead) and briefly while a player identity is being swapped.
     */
    public boolean isHiddenFromOthers(ServerPlayer player) {
        if (disguises.isEmpty() && pendingShow.isEmpty()) {
            return false;
        }

        UUID id = player.getUUID();

        if (pendingShow.containsKey(id)) {
            return true;
        }

        State state = disguises.get(id);
        return state != null && (state.skin == null || state.refreshing);
    }

    /** The disguise entity of this player (entity disguises only), or null. */
    public Entity getDisguise(ServerPlayer player) {
        State state = disguises.get(player.getUUID());
        return state == null ? null : state.entity;
    }

    /** Name of the impersonated player, or null (not disguised or disguised as an entity). */
    public String getPlayerDisguiseName(ServerPlayer player) {
        if (disguises.isEmpty()) return null;

        State state = disguises.get(player.getUUID());
        return state == null || state.skin == null ? null : state.skin.name();
    }

    /** Entity type of an entity disguise, or null. */
    public EntityType<?> getDisguiseType(ServerPlayer player) {
        State state = disguises.get(player.getUUID());
        return state == null ? null : state.type;
    }

    /** Is this entity somebody's disguise entity? */
    public boolean isDisguiseEntity(Entity entity) {
        return !entityOwners.isEmpty() && entityOwners.containsKey(entity.getUUID());
    }

    /** Owner of a disguise entity, or null if the entity is not a disguise. */
    public UUID getDisguiseOwner(Entity entity) {
        return entityOwners.isEmpty() ? null : entityOwners.get(entity.getUUID());
    }

    /** owner UUID -> readable description ("player Steve" / "Zombie"). */
    public Map<UUID, String> describeAll() {
        Map<UUID, String> result = new LinkedHashMap<>();

        for (State state : disguises.values()) {
            result.put(state.owner, state.skin != null
                    ? "player " + state.skin.name()
                    : state.type.getDescription().getString());
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

    /** Disguises the player as another online player (name + skin). */
    public boolean disguisePlayer(ServerPlayer player, ServerPlayer target) {
        if (player == target) {
            return false;
        }

        return disguiseAsProfile(player, target.getGameProfile());
    }

    /** Disguises the player as any profile (name + skin), e.g. one from {@link SkinLookup}. */
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

    /** Called when a player disconnects: clean up without touching the (already leaving) player. */
    public void onDisconnect(ServerPlayer player) {
        State state = disguises.get(player.getUUID());

        if (state != null && state.skin == null) {
            player.setInvisible(state.wasInvisible);
        }

        release(player.getUUID(), null);
    }

    /** Called when a player joins: hand him the swapped identities of players that are disguised right now. */
    public void onJoin(ServerPlayer joined) {
        // Safety net: invisibility left over from a crash while disguised
        if (!disguises.containsKey(joined.getUUID())
                && joined.isInvisible()
                && !joined.hasEffect(MobEffects.INVISIBILITY)) {
            joined.setInvisible(false);
        }

        MinecraftServer server = joined.level().getServer();

        for (State state : disguises.values()) {
            if (state.packetSource == null || state.owner.equals(joined.getUUID())) {
                continue;
            }

            ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);

            if (owner != null && !mayKnow(owner, joined)) {
                continue;
            }

            joined.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(state.owner)));
            joined.connection.send(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, state.packetSource));
        }
    }

    public void shutdown(MinecraftServer server) {
        for (State state : List.copyOf(disguises.values())) {
            if (state.skin == null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);

                if (owner != null) {
                    owner.setInvisible(state.wasInvisible);
                }
            }

            drop(state);
        }

        disguises.clear();
        entityOwners.clear();
        pendingShow.clear();
    }

    // ==================== internals ====================

    private boolean start(ServerPlayer player, State state) {
        undisguise(player);

        // Registered before spawning so the tracking mixin already hides the right entities
        disguises.put(state.owner, state);

        boolean ok;

        try {
            ok = state.skin != null ? applyIdentity(state, player) : startEntity(state, player);
        } catch (RuntimeException e) {
            LOGGER.error("[Disguise] Could not start disguise for {}", player.getName().getString(), e);
            ok = false;
        }

        if (!ok) {
            disguises.remove(state.owner);
            pendingShow.remove(state.owner);
            drop(state);
            refreshTracking(player);
            return false;
        }

        return true;
    }

    private boolean startEntity(State state, ServerPlayer player) {
        state.wasInvisible = player.isInvisible();

        if (!spawn(state, player)) {
            return false;
        }

        player.setInvisible(true);
        refreshTracking(player);
        return true;
    }

    /**
     * Player disguise, step 1: swap the identity other clients know for this UUID and hide the real
     * entity. Step 2 (a few ticks later, see {@link #tick}) shows it again so clients rebuild it.
     */
    private boolean applyIdentity(State state, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();

        // Same UUID as the real player (that's how clients match entity and identity), target's name + skin
        GameProfile identity = new GameProfile(player.getUUID(), state.skin.name());
        identity.properties().putAll(state.skin.properties());

        // Only a packet source, never added to the world
        state.packetSource = FakePlayer.get(level, identity);
        state.refreshing = true;

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player || !mayKnow(player, other)) {
                continue;
            }

            other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            other.connection.send(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, state.packetSource));
        }

        pendingShow.put(state.owner, server.getTickCount() + RESHOW_DELAY_TICKS);
        refreshTracking(player); // hides the real entity from everybody else for now
        return true;
    }

    /** Player disguise removal: hand everybody the real identity back and re-send the entity. */
    private void restoreIdentity(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player || !mayKnow(player, other)) {
                continue;
            }

            other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            other.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        }

        pendingShow.put(player.getUUID(), server.getTickCount() + RESHOW_DELAY_TICKS);
        refreshTracking(player);
    }

    /** Vanished players are unknown to viewers who may not see them: don't tell those anything. */
    private static boolean mayKnow(ServerPlayer subject, ServerPlayer viewer) {
        VanishManager vanish = VanishManager.getInstance();
        return !(vanish.isVanished(subject) && !vanish.canSee(viewer));
    }

    private void release(UUID owner, ServerPlayer refresh) {
        State state = disguises.remove(owner);

        if (state == null) {
            return;
        }

        drop(state);

        if (refresh == null || refresh.isRemoved()) {
            pendingShow.remove(owner);
            return;
        }

        if (state.skin != null) {
            restoreIdentity(refresh);
        } else {
            refresh.setInvisible(state.wasInvisible);
            refreshTracking(refresh);
        }
    }

    /** Removes the disguise entity of this state (entity disguises only). */
    private void drop(State state) {
        state.packetSource = null;
        state.refreshing = false;

        Entity entity = state.entity;
        state.entity = null;

        if (entity == null) {
            return;
        }

        entityOwners.remove(entity.getUUID());
        entity.discard();
    }

    private boolean spawn(State state, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Entity entity = state.type.create(level, EntitySpawnReason.COMMAND);

        if (entity == null) {
            return false;
        }

        prepare(entity, player);
        entityOwners.put(entity.getUUID(), state.owner);

        if (!level.addFreshEntity(entity)) {
            entityOwners.remove(entity.getUUID());
            entity.discard();
            return false;
        }

        state.entity = entity;
        return true;
    }

    private static void prepare(Entity entity, ServerPlayer player) {
        moveToPlayer(entity, player);
        entity.setNoGravity(true);
        entity.setSilent(true);
        entity.setPermanentlyInvulnerable(true);
        entity.setDeltaMovement(Vec3.ZERO);

        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }

        // Make the "dangerous" ones harmless
        if (entity instanceof PrimedTnt tnt) {
            tnt.setFuse(Integer.MAX_VALUE);
        }

        if (entity instanceof Projectile projectile) {
            projectile.setOwner(player); // a projectile never hits its own owner
        }
    }

    private boolean rebuild(State state, ServerPlayer player, MinecraftServer server) {
        int now = server.getTickCount();

        if (now - state.windowStart > REBUILD_WINDOW_TICKS) {
            state.windowStart = now;
            state.rebuilds = 0;
        }

        if (++state.rebuilds > MAX_REBUILDS) {
            return false;
        }

        drop(state);
        return spawn(state, player);
    }

    private void tick(MinecraftServer server) {
        if (!pendingShow.isEmpty()) {
            tickPendingShow(server);
        }

        if (disguises.isEmpty()) {
            return;
        }

        for (State state : List.copyOf(disguises.values())) {
            try {
                tickState(state, server);
            } catch (RuntimeException e) {
                // A broken disguise must never take the whole server down
                LOGGER.error("[Disguise] Error while ticking a disguise, removing it", e);
                disguises.remove(state.owner);
                pendingShow.remove(state.owner);

                try {
                    drop(state);
                } catch (RuntimeException ignored) {
                    // nothing more we can do
                }
            }
        }
    }

    private void tickPendingShow(MinecraftServer server) {
        int now = server.getTickCount();

        for (Map.Entry<UUID, Integer> entry : List.copyOf(pendingShow.entrySet())) {
            if (now < entry.getValue()) {
                continue;
            }

            pendingShow.remove(entry.getKey());

            State state = disguises.get(entry.getKey());

            if (state != null) {
                state.refreshing = false;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (player != null && !player.isRemoved()) {
                refreshTracking(player); // real entity is sent again, clients now know the new identity
            }
        }
    }

    private void tickState(State state, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.owner);

        if (player == null || player.isRemoved()) {
            release(state.owner, null);
            return;
        }

        if (!player.isAlive()) {
            release(state.owner, player);
            return;
        }

        if (state.skin != null) {
            return; // player disguise: nothing to follow, the real entity is the disguise
        }

        if (player.isSpectator()) {
            release(state.owner, player);
            player.sendSystemMessage(Component.literal("§c✗ Your disguise was removed (spectator mode)"));
            return;
        }

        Entity disguise = state.entity;

        if (disguise == null || disguise.isRemoved() || disguise.level() != player.level()) {
            if (!rebuild(state, player, server)) {
                release(state.owner, player);
                player.sendSystemMessage(Component.literal("§c✗ Your disguise was removed (it could not be kept)"));
                return;
            }

            // Hide the real player / new entity again for the new world
            refreshTracking(player);
            disguise = state.entity;
        }

        if (!player.isInvisible()) {
            player.setInvisible(true);
        }

        moveToPlayer(disguise, player);
        disguise.setDeltaMovement(Vec3.ZERO);
        disguise.setOnGround(player.onGround());
    }

    private static void moveToPlayer(Entity entity, ServerPlayer player) {
        entity.setPos(player.getX(), player.getY(), player.getZ());
        entity.setYRot(player.getYRot());
        entity.setXRot(player.getXRot());

        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(player.getYHeadRot());
            living.setYBodyRot(player.getYRot());
        }
    }

    /** Re-evaluates who may see the player (same call vanish uses; doesn't reload chunks). */
    private static void refreshTracking(ServerPlayer player) {
        ((ServerLevel) player.level()).getChunkSource().move(player);
    }
}
