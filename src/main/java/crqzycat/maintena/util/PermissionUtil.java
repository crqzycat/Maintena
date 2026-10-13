package crqzycat.maintena.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Berechtigungen für alle Maintena-Befehle (Admin-Tools): erlaubt ist, wer Operator ist
 * (Admin-Level) ODER die passende Permission über LuckPerms hat. Alle anderen Spieler nicht.
 *
 * <p>LuckPerms wird als optionale Abhängigkeit über die fabric-permissions-api angesprochen
 * (per Reflection, damit Maintena auch ohne LuckPerms startet). Ist sie nicht vorhanden,
 * gilt nur die Operator-Regel.
 */
public final class PermissionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    // Jeder Befehl hat eine eigene Node. Mit LuckPerms geht auch ein Wildcard: maintena.*
    public static final String MENU = "maintena.menu";               // /maintena und das Einstellungsmenü
    public static final String MAINTENANCE = "maintena.maintenance"; // /maintenance
    public static final String RESTART = "maintena.restart";         // /restart
    public static final String ANNOUNCE = "maintena.announce";       // /announce
    public static final String BAN = "maintena.ban";                 // /ban, /unban, /banlist
    public static final String IP_BAN = "maintena.ipban";            // /ip, /ipban, /ipunban, /ipbanlist
    public static final String MUTE = "maintena.mute";               // /mute, /unmute, /mutelist
    public static final String FREEZE = "maintena.freeze";           // /freeze, /unfreeze
    public static final String VANISH = "maintena.vanish";           // /vanish (wer es nutzen darf, sieht auch Vanished)
    public static final String NICK = "maintena.nick";               // /nick
    public static final String DISGUISE = "maintena.disguise";       // /disguise
    public static final String ALIAS = "maintena.alias";             // /alias

    /** Permissions.check(source, node, boolean defaultValue) der fabric-permissions-api, oder null. */
    private static final Method CHECK = findCheckMethod();

    private PermissionUtil() {
    }

    /**
     * @return true für Operatoren (Admin-Level) und Konsole, sonst true nur, wenn die Permission
     *         ausdrücklich vergeben ist (Standard ohne Eintrag: nein)
     */
    public static boolean has(CommandSourceStack source, String node) {
        if (source.permissions().hasPermission(Permissions.COMMANDS_ADMIN)) {
            return true;
        }

        return checkPermissionApi(source, node);
    }

    /** Für .requires(...) in den Befehlsbäumen. */
    public static Predicate<CommandSourceStack> requires(String node) {
        return source -> has(source, node);
    }

    private static boolean checkPermissionApi(CommandSourceStack source, String node) {
        if (CHECK == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(CHECK.invoke(null, source, node, false));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method findCheckMethod() {
        try {
            Class<?> api = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");

            for (Method method : api.getMethods()) {
                Class<?>[] params = method.getParameterTypes();

                if (method.getName().equals("check")
                        && params.length == 3
                        && params[0].isAssignableFrom(CommandSourceStack.class)
                        && params[1] == String.class
                        && params[2] == boolean.class) {
                    LOGGER.info("[Permissions] Permission API found, Maintena commands also accept LuckPerms nodes");
                    return method;
                }
            }

            LOGGER.warn("[Permissions] Permission API found, but without a usable check method (operators only)");
        } catch (ClassNotFoundException e) {
            LOGGER.info("[Permissions] No permission API (LuckPerms) found, Maintena commands are for operators only");
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("[Permissions] Could not use the permission API (operators only)", e);
        }

        return null;
    }
}
