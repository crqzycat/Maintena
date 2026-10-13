package crqzycat.maintena.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entfernt Befehle aus dem Befehlsbaum. Brigadier bietet dafür keine API, deshalb greift das
 * hier per Reflection in die Knoten-Maps ein. Das passiert nur an dieser einen Stelle.
 *
 * <p>Die Felder werden nach Typ gesucht (alle Map-Felder von CommandNode: children, literals,
 * arguments) und nicht nach festen Namen, damit eine Umbenennung in Brigadier nichts kaputt
 * macht. Nach dem Entfernen wird geprüft, ob der Befehl wirklich weg ist; wenn nicht, steht ein
 * deutlicher Fehler im Log.
 */
public final class CommandTreeUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    private static final List<Field> NAME_MAPS = findNameMaps();

    private CommandTreeUtil() {
    }

    /**
     * Entfernt Befehle der obersten Ebene (z.B. "ban") aus dem Dispatcher.
     *
     * @return true, wenn alle genannten Befehle danach nicht mehr im Baum sind
     */
    public static boolean removeRootChildren(CommandDispatcher<CommandSourceStack> dispatcher, String... names) {
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        boolean allRemoved = true;

        for (String name : names) {
            if (root.getChild(name) == null) {
                continue; // gibt es nicht (mehr), nichts zu tun
            }

            for (Field field : NAME_MAPS) {
                try {
                    if (field.get(root) instanceof Map<?, ?> map) {
                        map.remove(name);
                    }
                } catch (IllegalAccessException | RuntimeException e) {
                    LOGGER.error("[Commands] Could not change {} while removing /{}", field.getName(), name, e);
                }
            }

            if (root.getChild(name) != null) {
                LOGGER.error("[Commands] Could not remove /{} from the command tree, "
                        + "the Brigadier version probably changed", name);
                allRemoved = false;
            }
        }

        return allRemoved;
    }

    private static List<Field> findNameMaps() {
        List<Field> fields = new ArrayList<>();

        for (Field field : CommandNode.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                fields.add(field);
            } catch (RuntimeException e) {
                LOGGER.error("[Commands] Could not access CommandNode.{}", field.getName(), e);
            }
        }

        if (fields.isEmpty()) {
            LOGGER.error("[Commands] No usable fields found in Brigadier's CommandNode, "
                    + "vanilla commands can't be replaced");
        }

        return fields;
    }
}
