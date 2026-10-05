package crqzycat.maintena.disguise;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/** Resolves entity IDs on Minecraft 26.3. */
public final class DisguiseType {
    private DisguiseType() {}

    public static Optional<EntityType<?>> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();

        String normalized = id.toLowerCase();
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;

        Identifier identifier;
        try {
            identifier = Identifier.parse(normalized);
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier);
    }
}
