package crqzycat.maintena.gui;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oeffnet die Settings-GUI und verarbeitet die Klicks auf ihre Buttons. Das Menue ist nur fuer
 * Admins gedacht und wird ueber /maintena geoeffnet:
 *
 *   /maintena               Hauptmenue
 *   /maintena maintenance   Maintenance-Menue
 *   /maintena restart       Restart-Menue
 *   /maintena announce      Announcement-Menue
 *   /maintena ban           Ban-Menue
 *   /maintena ipban         IP-Ban-Menue
 *
 * Die Rechte-Pruefung macht der /maintena Befehl selbst (Gamemaster/OP). Die Buttons senden eine
 * custom click action; der Server fuehrt den Befehl mit den Rechten des Spielers aus.
 */
public final class SettingsGUIHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    /** Id (ohne Namespace) der Custom-Click-Action aller Menue-Buttons: maintena:run */
    public static final String ACTION_PATH = "run";
    public static final String ACTION_ID = "maintena:" + ACTION_PATH;

    /** Schluessel im Payload: Befehl (mit $(key)-Platzhaltern), optional. */
    public static final String KEY_COMMAND = "mt_cmd";
    /** Schluessel im Payload: Seite, die nach dem Befehl (bzw. direkt) geoeffnet wird. */
    public static final String KEY_PAGE = "mt_page";
    /** Schluessel im Payload: Parameter der Seite (z.B. Spielername), optional. */
    public static final String KEY_ARG = "mt_arg";

    /** Nur Befehle dieser Maintena-Bereiche duerfen ueber die Menue-Buttons laufen. */
    private static final Set<String> ALLOWED_ROOTS = Set.of(
            "maintena", "maintenance", "restart", "announce",
            "ban", "unban", "banlist", "ip", "ipban", "ipunban", "ipbanlist"
    );

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\(([A-Za-z0-9_]+)\\)");

    private SettingsGUIHandler() {
    }

    public enum Page {
        MAIN,
        MAINTENANCE,
        MAINTENANCE_WHITELIST,
        MAINTENANCE_PLAYER,
        RESTART,
        RESTART_SCHEDULES,
        RESTART_SCHEDULE,
        RESTART_ADD,
        ANNOUNCE,
        ANNOUNCE_SCHEDULES,
        ANNOUNCE_SCHEDULE,
        ANNOUNCE_ADD,
        BAN,
        BAN_LIST,
        BAN_ENTRY,
        IP_BAN,
        IPBAN_LIST,
        IPBAN_ENTRY,
        IP_ONLINE,
        IP_PLAYER
    }

    /** Fuer Brigadier: oeffnet die Seite fuer den ausfuehrenden Spieler. */
    public static int open(CommandContext<CommandSourceStack> ctx, Page page) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("§c✗ This menu can only be opened by a player in-game"));
            return 0;
        }

        return show(source, player, page, null) ? 1 : 0;
    }

    private static boolean show(CommandSourceStack source, ServerPlayer player, Page page, String arg) {
        try {
            Dialog dialog = SettingsGUI.build(page, source, arg);
            player.openDialog(Holder.direct(dialog));
            return true;
        } catch (Exception e) {
            // Fehler nicht verschlucken: in die Konsole und zum Spieler
            LOGGER.error("Could not open Maintena menu page {}", page, e);
            source.sendFailure(Component.literal(
                    "§c✗ Could not open the menu (" + page + "): " + e
            ));
            return false;
        }
    }

    /**
     * Wird vom Mixin aufgerufen, wenn ein Menue-Button geklickt wurde (custom click action).
     * Laeuft im Netzwerk-Thread, die Ausfuehrung wird auf den Server-Thread verschoben.
     */
    public static void handleClick(ServerPlayer player, Tag payload) {
        if (!(payload instanceof CompoundTag compound)) {
            return;
        }

        MinecraftServer server = player.level().getServer();

        server.execute(() -> {
            CommandSourceStack source = player.createCommandSourceStack();

            // Das Menue ist nur fuer Admins; die Befehle pruefen die Rechte zusaetzlich selbst.
            if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                return;
            }

            String template = compound.getStringOr(KEY_COMMAND, "");

            if (!template.isBlank()) {
                String command = fillPlaceholders(template, compound).strip();
                String root = command.split(" ", 2)[0];

                if (!ALLOWED_ROOTS.contains(root)) {
                    LOGGER.warn("Ignored menu action with unexpected command from {}: {}",
                            player.getPlainTextName(), command);
                    return;
                }

                server.getCommands().performPrefixedCommand(source, command);
            }

            // Danach die gewuenschte Seite oeffnen (aktualisiert die Anzeige bzw. navigiert)
            Page page = parsePage(compound.getStringOr(KEY_PAGE, ""));

            if (page != null) {
                String arg = compound.getStringOr(KEY_ARG, "").replaceAll("\\s", "");

                if (arg.length() > 64) {
                    arg = arg.substring(0, 64);
                }

                show(source, player, page, arg.isEmpty() ? null : arg);
            }
        });
    }

    private static Page parsePage(String name) {
        try {
            return Page.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Ersetzt $(key) durch den Text des gleichnamigen Eingabefelds (Steuerzeichen entfernt). */
    private static String fillPlaceholders(String template, CompoundTag values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String value = values.getStringOr(matcher.group(1), "")
                    .replaceAll("\\p{Cntrl}", " ")
                    .strip();

            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
