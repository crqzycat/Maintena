package crqzycat.maintena.disguise;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;

/**
 * Asks the morph's template entity which sounds it would make. The sound getters are protected in
 * vanilla, so they are called reflectively. If a lookup fails the feature simply stays off
 * (the player keeps his own sounds) - it can never break the server.
 */
public final class DisguiseSounds {

    private static final Method HURT = find(LivingEntity.class, "getHurtSound", DamageSource.class);
    private static final Method DEATH = find(LivingEntity.class, "getDeathSound");
    private static final Method AMBIENT = find(Mob.class, "getAmbientSound");

    private DisguiseSounds() {}

    public static boolean hasHurt() {
        return HURT != null;
    }

    public static boolean hasDeath() {
        return DEATH != null;
    }

    /** Hurt sound of the morph; null = silent (e.g. boats). */
    public static SoundEvent hurt(Entity template, DamageSource source) {
        return call(HURT, template, source);
    }

    /** Death sound of the morph; null = silent. */
    public static SoundEvent death(Entity template) {
        return call(DEATH, template);
    }

    /** Idle sound of the morph (mobs only); null = none. */
    public static SoundEvent ambient(Entity template) {
        return template instanceof Mob ? call(AMBIENT, template) : null;
    }

    private static SoundEvent call(Method method, Entity template, Object... args) {
        if (method == null || !(template instanceof LivingEntity)) {
            return null;
        }

        try {
            return (SoundEvent) method.invoke(template, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
