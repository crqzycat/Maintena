package crqzycat.maintena.disguise;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mob and player disguises.
 *
 * <p>How it works: the disguise is a separate entity that follows the player. The real player is
 * hidden from every other viewer by {@code MaintenaDisguiseTrackingMixin} and additionally set
 * invisible, so the owner sees the disguise (third person) instead of his own body. The previous
 * invisibility state is restored on undisguise, disconnect and server stop.
 *
 * <p>For player disguises the fake player gets its own random UUID (never the owner's or the target's)
 * and the clients receive a hidden (unlisted) player-info entry so name and skin are known before the
 * entity is spawned.
 */
public final class DisguiseManager {

    private static final DisguiseManager INSTANCE = new DisguiseManager();

    /** More than this many rebuilds inside the window means the entity can't be kept alive (e.g. Peaceful). */
    private static final int MAX_REBUILDS = 5;
    private static final int REBUILD_WINDOW_TICKS = 100;

    private static final class State {
        final UUID owner;
        /** Mob disguise: the entity type (null for player disguises). */
        final EntityType<?> type;
        /** Player disguise: the profile (name + skin) of the target (null for mob disguises). */
        final GameProfile skin;

        /** Invisibility of the player before the disguise, restored afterwards. */
        boolean wasInvisible;

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
    /** disguise entity UUID -> owner UUID. */
    private final Map<UUID, UUID> entityOwners = new ConcurrentHashMap<>();

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

    /** The disguise entity of this player, or null. */
    public Entity getDisguise(ServerPlayer player) {
        State state = disguises.get(player.getUUID());
        return state == null ? null : state.entity;
    }

    /** Name of the impersonated player, or null (not disguised or disguised as a mob). */
    public String getPlayerDisguiseName(ServerPlayer player) {
        if (disguises.isEmpty()) return null;

        State state = disguises.get(player.getUUID());
        return state == null || state.skin == null ? null : state.skin.name();
    }

    /** Entity type of a mob disguise, or null. */
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

        if (state != null) {
            player.setInvisible(state.wasInvisible);
        }

        release(player.getUUID(), null);
    }

    /** Called when a player joins: hand him the hidden player-info entries of active player disguises. */
    public void onJoin(ServerPlayer joined) {
        // Safety net: invisibility left over from a crash while disguised
        if (!disguises.containsKey(joined.getUUID())
                && joined.isInvisible()
                && !joined.hasEffect(MobEffects.INVISIBILITY)) {
            joined.setInvisible(false);
        }

        for (State state : disguises.values()) {
            if (state.entity instanceof ServerPlayer fake && !state.owner.equals(joined.getUUID())) {
                joined.connection.send(new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, fake));
            }
        }
    }

    public void shutdown(MinecraftServer server) {
        for (State state : List.copyOf(disguises.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);

            if (owner != null) {
                owner.setInvisible(state.wasInvisible);
            }

            drop(state);
        }

        disguises.clear();
        entityOwners.clear();
    }

    // ==================== internals ====================

    private boolean start(ServerPlayer player, State state) {
        undisguise(player);

        // Registered before spawning so the tracking mixin already hides the right entities
        state.wasInvisible = player.isInvisible();
        disguises.put(state.owner, state);

        if (!spawn(state, player)) {
            disguises.remove(state.owner);
            return false;
        }

        player.setInvisible(true);
        refreshTracking(player);
        return true;
    }

    private void release(UUID owner, ServerPlayer refresh) {
        State state = disguises.remove(owner);

        if (state == null) {
            return;
        }

        drop(state);

        if (refresh != null && !refresh.isRemoved()) {
            refresh.setInvisible(state.wasInvisible);
            refreshTracking(refresh);
        }
    }

    /** Removes the disguise entity of this state (and the hidden player-info entry for player disguises). */
    private void drop(State state) {
        Entity entity = state.entity;
        state.entity = null;

        if (entity == null) {
            return;
        }

        entityOwners.remove(entity.getUUID());

        MinecraftServer server = entity.level().getServer();
        entity.discard();

        if (entity instanceof ServerPlayer fake && server != null) {
            sendInfoRemove(server, fake.getUUID());
        }
    }

    private boolean spawn(State state, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Entity entity = createEntity(state, level);

        if (entity == null) {
            return false;
        }

        prepare(entity, player);
        entityOwners.put(entity.getUUID(), state.owner);

        // Clients must know name + skin before the player entity reaches them
        if (entity instanceof ServerPlayer fake) {
            sendInfoAdd(level.getServer(), fake, player);
        }

        if (!level.addFreshEntity(entity)) {
            entityOwners.remove(entity.getUUID());

            if (entity instanceof ServerPlayer fake) {
                sendInfoRemove(level.getServer(), fake.getUUID());
            }

            entity.discard();
            return false;
        }

        state.entity = entity;
        return true;
    }

    private static Entity createEntity(State state, ServerLevel level) {
        if (state.skin != null) {
            // Fresh random UUID: never collides with a real player, and FakePlayer's cache can't hand
            // back an instance that was already discarded
            GameProfile fakeProfile = new GameProfile(UUID.randomUUID(), state.skin.name());
            fakeProfile.properties().putAll(state.skin.properties());
            return FakePlayer.get(level, fakeProfile);
        }

        return state.type.create(level, EntitySpawnReason.COMMAND);
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
        if (disguises.isEmpty()) {
            return;
        }

        for (State state : List.copyOf(disguises.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.owner);

            if (player == null || player.isRemoved()) {
                release(state.owner, null);
                continue;
            }

            if (!player.isAlive()) {
                release(state.owner, player);
                continue;
            }

            if (player.isSpectator()) {
                release(state.owner, player);
                player.sendSystemMessage(Component.literal("§c✗ Your disguise was removed (spectator mode)"));
                continue;
            }

            Entity disguise = state.entity;

            if (disguise == null || disguise.isRemoved() || disguise.level() != player.level()) {
                if (!rebuild(state, player, server)) {
                    release(state.owner, player);
                    player.sendSystemMessage(Component.literal("§c✗ Your disguise was removed (it could not be kept)"));
                    continue;
                }

                // Hide the real player / new entity again for the new world
                refreshTracking(player);
                disguise = state.entity;
            }

            if (!player.isInvisible()) {
                player.setInvisible(true);
            }

            sync(state, disguise, player);
        }
    }

    private static void sync(State state, Entity disguise, ServerPlayer player) {
        moveToPlayer(disguise, player);
        disguise.setDeltaMovement(Vec3.ZERO);
        disguise.setOnGround(player.onGround());

        if (state.skin != null) {
            disguise.setPose(player.getPose());
            disguise.setShiftKeyDown(player.isShiftKeyDown());
            disguise.setSprinting(player.isSprinting());
        }
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

    /** Hidden (unlisted) player-info entry: gives clients name + skin without showing a tab-list entry. */
    private static void sendInfoAdd(MinecraftServer server, ServerPlayer fake, ServerPlayer owner) {
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, fake);

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != owner) {
                other.connection.send(packet);
            }
        }
    }

    private static void sendInfoRemove(MinecraftServer server, UUID fakeUuid) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(fakeUuid));

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            other.connection.send(packet);
        }
    }
}
