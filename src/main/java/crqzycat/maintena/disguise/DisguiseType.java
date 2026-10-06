package crqzycat.maintena.disguise;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Resolves entity IDs on Minecraft 26.3 and decides which entities may be used as a disguise. */
public final class DisguiseType {

    /**
     * The only entities that can't be used. Everything else (mobs, bosses, vehicles, projectiles,
     * display entities, ...) works.
     * <ul>
     *   <li>item, experience_orb: items/orbs (excluded on purpose, orbs would be picked up at once)</li>
     *   <li>player, fishing_bobber, lightning_bolt: technical entities (use /disguise &lt;player&gt;)</li>
     *   <li>falling_block, painting, item_frame, glow_item_frame, leash_knot: turn into a block or drop
     *       an item every few seconds when they don't sit where they should</li>
     * </ul>
     */
    private static final Set<String> BLOCKED = Set.of(
            "minecraft:item",
            "minecraft:experience_orb",
            "minecraft:player",
            "minecraft:fishing_bobber",
            "minecraft:lightning_bolt",
            "minecraft:falling_block",
            "minecraft:painting",
            "minecraft:item_frame",
            "minecraft:glow_item_frame",
            "minecraft:leash_knot"
    );

    private DisguiseType() {}

    public static Optional<EntityType<?>> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();

        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;

        Identifier identifier;
        try {
            identifier = Identifier.parse(normalized);
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier);
    }

    /** Summonable and not on the (short) block list. */
    public static boolean isAllowed(EntityType<?> type) {
        if (type == null || !type.canSummon()) return false;
        return !BLOCKED.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    /** All allowed entity IDs starting with the typed text (vanilla IDs without the namespace). */
    public static List<String> suggestions(String typedLowerCase) {
        List<String> result = new ArrayList<>();

        for (Identifier key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String full = key.toString();
            String shown = "minecraft".equals(key.getNamespace()) ? key.getPath() : full;

            if (!full.startsWith(typedLowerCase) && !shown.startsWith(typedLowerCase)) continue;

            boolean allowed = BuiltInRegistries.ENTITY_TYPE.getOptional(key)
                    .filter(DisguiseType::isAllowed)
                    .isPresent();

            if (allowed) result.add(shown);
        }

        Collections.sort(result);
        return result;
    }
}
