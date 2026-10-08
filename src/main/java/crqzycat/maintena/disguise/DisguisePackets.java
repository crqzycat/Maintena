package crqzycat.maintena.disguise;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The heart of the entity morph. The disguised player stays a normal player on the server (same
 * entity id, same position, real damage, real hitbox). Only what OTHER players receive is rewritten
 * on its way out:
 * <ul>
 *   <li>the spawn packet announces the morph's entity type instead of "player",</li>
 *   <li>entity data is replaced by the morph's own data (player-only fields would crash clients),</li>
 *   <li>packets that clients can only apply to living entities are dropped for non-living morphs.</li>
 * </ul>
 * Movement, rotation, hurt animations, passengers, ... are keyed by entity id and just work.
 * The owner himself is never rewritten (his client still has a LocalPlayer under that id).
 */
public final class DisguisePackets {

    private static final int FLAGS_ID = 0;

    /** fire, crouch, sprint, swim, invisible, glowing. Everything else (elytra, ...) is player-only. */
    private static final byte FLAG_MASK = 0x7B;

    /** True while an already rewritten packet is being re-sent (prevents endless recursion). */
    private static final ThreadLocal<Boolean> REWRITING = ThreadLocal.withInitial(() -> false);

    /** Set once the send hook has run at least once (used for a startup sanity check). */
    private static volatile boolean hookActive;

    private DisguisePackets() {}

    public static boolean isHookActive() {
        return hookActive;
    }

    /**
     * Called by the send mixins for every outgoing game packet.
     *
     * @return true if the original packet must NOT be sent (it was replaced or dropped)
     */
    public static boolean intercept(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        hookActive = true;

        if (REWRITING.get() || !DisguiseManager.getInstance().hasMorphs()) {
            return false;
        }

        ServerPlayer viewer = connection.getPlayer();

        if (viewer == null) {
            return false;
        }

        Packet<?> result = rewrite(packet, viewer.getId());

        if (result == packet) {
            return false;
        }

        if (result != null) {
            REWRITING.set(true);

            try {
                connection.send(result);
            } finally {
                REWRITING.set(false);
            }
        }

        return true;
    }

    /** @return the same packet (unchanged), a replacement, or null (drop it) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Packet<?> rewrite(Packet<?> packet, int viewerId) {
        DisguiseManager manager = DisguiseManager.getInstance();

        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            boolean changed = false;

            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                Packet<?> result = rewrite(sub, viewerId);

                if (result != sub) {
                    changed = true;
                }

                if (result != null) {
                    out.add((Packet) result);
                }
            }

            return changed ? new ClientboundBundlePacket(out) : bundle;
        }

        if (packet instanceof ClientboundAddEntityPacket add) {
            DisguiseManager.Morph morph = morphOf(manager, add.getId(), viewerId);

            // Already announced as the morph type (e.g. rewritten before): leave it alone.
            if (morph == null || add.getType() == morph.type()) {
                return packet;
            }

            return new ClientboundAddEntityPacket(
                    add.getId(), add.getUUID(),
                    add.getX(), add.getY(), add.getZ(),
                    add.getXRot(), add.getYRot(),
                    morph.type(), 0, Vec3.ZERO, add.getYHeadRot());
        }

        if (packet instanceof ClientboundSetEntityDataPacket data) {
            DisguiseManager.Morph morph = morphOf(manager, data.id(), viewerId);

            if (morph == null) {
                return packet;
            }

            List<SynchedEntityData.DataValue<?>> out = new ArrayList<>();

            // Only the shared flags come from the real player; the rest is the morph's own data.
            for (SynchedEntityData.DataValue<?> value : data.packedItems()) {
                if (value.id() == FLAGS_ID && value.value() instanceof Byte flags) {
                    out.add(new SynchedEntityData.DataValue<>(
                            FLAGS_ID, EntityDataSerializers.BYTE, (byte) (flags & FLAG_MASK)));
                }
            }

            out.addAll(morph.extras());

            return out.isEmpty() ? null : new ClientboundSetEntityDataPacket(data.id(), out);
        }

        // Player attributes make no sense on the morph (and throw on non-living ones).
        if (packet instanceof ClientboundUpdateAttributesPacket attributes) {
            return morphOf(manager, attributes.getEntityId(), viewerId) != null ? null : packet;
        }

        // Living-only packets: the client casts the entity to LivingEntity.
        if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            return dropForNonLiving(manager, equipment.getEntity(), viewerId, packet);
        }

        if (packet instanceof ClientboundAnimatePacket animate) {
            return dropForNonLiving(manager, animate.getId(), viewerId, packet);
        }

        if (packet instanceof ClientboundTakeItemEntityPacket take) {
            return dropForNonLiving(manager, take.getPlayerId(), viewerId, packet);
        }

        return packet;
    }

    private static Packet<?> dropForNonLiving(DisguiseManager manager, int entityId, int viewerId, Packet<?> packet) {
        DisguiseManager.Morph morph = morphOf(manager, entityId, viewerId);

        return morph != null && !morph.living() ? null : packet;
    }

    private static DisguiseManager.Morph morphOf(DisguiseManager manager, int entityId, int viewerId) {
        return entityId == viewerId ? null : manager.getMorph(entityId);
    }
}
