package crqzycat.maintena.disguise;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages mob and player disguises.
 */
public final class DisguiseManager {

    private static final DisguiseManager INSTANCE = new DisguiseManager();

    private final Map<UUID, Entity> disguises = new HashMap<>();
    private final Map<UUID, String> playerDisguiseNames = new HashMap<>();

    private DisguiseManager() {
    }

    public static DisguiseManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    public boolean isDisguised(ServerPlayer player) {
        return disguises.containsKey(player.getUUID());
    }

    public Entity getDisguise(ServerPlayer player) {
        return disguises.get(player.getUUID());
    }

    public String getPlayerDisguiseName(ServerPlayer player) {
        return playerDisguiseNames.get(player.getUUID());
    }

    public boolean disguiseMob(ServerPlayer player, EntityType<?> type) {
        undisguise(player);

        Entity entity = type.create(
                player.level(),
                EntitySpawnReason.COMMAND
        );

        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }

        if (living instanceof Mob mob) {
            mob.setNoAi(true);
        }

        living.setPermanentlyInvulnerable(true);
        living.setNoGravity(true);
        moveToPlayer(living, player);

        if (!player.level().addFreshEntity(living)) {
            living.discard();
            return false;
        }

        disguises.put(player.getUUID(), living);
        player.setInvisible(true);
        return true;
    }

    /**
     * Disguises the player as another online player's skin and name.
     * The fake player uses the disguiser's UUID to avoid colliding with
     * the target player's UUID.
     */
    public boolean disguisePlayer(ServerPlayer player, ServerPlayer target) {
        if (player == target) {
            return false;
        }

        undisguise(player);

        GameProfile targetProfile = target.getGameProfile();

        GameProfile fakeProfile = new GameProfile(
                player.getUUID(),
                targetProfile.name()
        );

        fakeProfile.properties().putAll(targetProfile.properties());

        ServerLevel level = (ServerLevel) player.level();
        FakePlayer fakePlayer = FakePlayer.get(level, fakeProfile);

        fakePlayer.setPos(
                player.getX(),
                player.getY(),
                player.getZ()
        );

        fakePlayer.setYRot(player.getYRot());
        fakePlayer.setXRot(player.getXRot());
        fakePlayer.setYHeadRot(player.getYHeadRot());
        fakePlayer.setYBodyRot(player.getYRot());
        fakePlayer.setNoGravity(true);
        fakePlayer.setPermanentlyInvulnerable(true);

        if (!level.addFreshEntity(fakePlayer)) {
            fakePlayer.discard();
            return false;
        }

        disguises.put(player.getUUID(), fakePlayer);
        playerDisguiseNames.put(
                player.getUUID(),
                targetProfile.name()
        );

        player.setInvisible(true);
        return true;
    }

    public boolean undisguise(ServerPlayer player) {
        Entity entity = disguises.remove(player.getUUID());
        playerDisguiseNames.remove(player.getUUID());

        if (entity == null) {
            return false;
        }

        entity.discard();
        player.setInvisible(false);
        return true;
    }

    private void tick(MinecraftServer server) {
        if (disguises.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Entity> entry :
                Map.copyOf(disguises).entrySet()) {

            UUID playerUuid = entry.getKey();
            Entity disguise = entry.getValue();

            ServerPlayer player =
                    server.getPlayerList().getPlayer(playerUuid);

            if (player == null
                    || player.isRemoved()
                    || !player.isAlive()) {

                if (disguise != null) {
                    disguise.discard();
                }

                disguises.remove(playerUuid);
                playerDisguiseNames.remove(playerUuid);
                continue;
            }

            if (disguise.isRemoved()
                    || disguise.level() != player.level()) {

                String playerName =
                        playerDisguiseNames.get(playerUuid);

                Entity replacement = null;

                if (playerName != null) {
                    ServerPlayer target =
                            server.getPlayerList()
                                    .getPlayerByName(playerName);

                    if (target != null && target != player) {
                        GameProfile targetProfile =
                                target.getGameProfile();

                        GameProfile fakeProfile = new GameProfile(
                                player.getUUID(),
                                targetProfile.name()
                        );

                        fakeProfile.properties()
                                .putAll(targetProfile.properties());

                        replacement = FakePlayer.get(
                                (ServerLevel) player.level(),
                                fakeProfile
                        );
                    }
                } else {
                    replacement = disguise.getType().create(
                            player.level(),
                            EntitySpawnReason.COMMAND
                    );
                }

                if (replacement == null) {
                    undisguise(player);
                    continue;
                }

                replacement.setPos(
                        player.getX(),
                        player.getY(),
                        player.getZ()
                );

                replacement.setYRot(player.getYRot());
                replacement.setXRot(player.getXRot());
                replacement.setNoGravity(true);
                replacement.setPermanentlyInvulnerable(true);

                if (replacement instanceof Mob mob) {
                    mob.setNoAi(true);
                }

                if (!player.level().addFreshEntity(replacement)) {
                    replacement.discard();
                    undisguise(player);
                    continue;
                }

                disguise.discard();

                disguises.put(playerUuid, replacement);
                disguise = replacement;
            }

            moveToPlayer(disguise, player);
            player.setInvisible(true);
        }
    }

    private static void moveToPlayer(
            Entity entity,
            ServerPlayer player
    ) {
        entity.setPos(
                player.getX(),
                player.getY(),
                player.getZ()
        );

        entity.setYRot(player.getYRot());
        entity.setXRot(player.getXRot());

        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(player.getYHeadRot());
            living.setYBodyRot(player.getYRot());
        }
    }

    public void shutdown() {
        for (Entity entity : disguises.values()) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }

        disguises.clear();
        playerDisguiseNames.clear();
    }
}