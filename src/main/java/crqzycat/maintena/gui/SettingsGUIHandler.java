package crqzycat.maintena.gui;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oeffnet die Settings-GUI. Das Menue ist nur fuer Admins gedacht und wird ausschliesslich ueber
 * /maintena geoeffnet:
 *
 *   /maintena               Hauptmenue
 *   /maintena maintenance   Maintenance-Menue
 *   /maintena restart       Restart-Menue
 *   /maintena announce      Announcement-Menue
 *   /maintena ban           Ban-Menue
 *   /maintena ipban         IP-Ban-Menue
 *
 * Die Rechte-Pruefung macht der /maintena Befehl selbst (Gamemaster/OP), deshalb gibt es hier
 * keine zweite Pruefung. Die Buttons im Dialog fuehren normale Befehle mit den Rechten des
 * Spielers aus.
 */
public final class SettingsGUIHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    /** Id (ohne Namespace) der Custom-Click-Action aller Menü-Buttons: maintena:run */
    public static final String ACTION_PATH = "run";
    public static final String ACTION_ID = "maintena:" + ACTION_PATH;

    /** Schlüssel im Payload, unter dem der Befehl (mit $(key)-Platzhaltern) steht. */
    public static final String COMMAND_KEY = "cmd";

    /** Nur Befehle dieser Maintena-Bereiche dürfen über die Menü-Buttons laufen. */
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
        RESTART,
        RESTART_ADD,
        ANNOUNCE,
        ANNOUNCE_ADD,
        BAN,
        IP_BAN
    }

    /** Fuer Brigadier: oeffnet die Seite fuer den ausfuehrenden Spieler. */
    public static int open(CommandContext<CommandSourceStack> ctx, Page page) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("§c✗ This menu can only be opened by a player in-game"));
            return 0;
        }

        try {
            Dialog dialog = SettingsGUI.build(page, source);
            player.openDialog(Holder.direct(dialog));
            return 1;
        } catch (Exception e) {
            // Fehler nicht verschlucken: in die Konsole und zum Spieler
            LOGGER.error("Could not open Maintena menu page {}", page, e);
            source.sendFailure(Component.literal(
                    "§c✗ Could not open the menu (" + page + "): " + e
            ));
            return 0;
        }
    }

    /**
     * Wird vom Mixin aufgerufen, wenn ein Menü-Button geklickt wurde (custom click action).
     * Läuft im Netzwerk-Thread, die Ausführung wird auf den Server-Thread verschoben.
     */
    public static void handleClick(ServerPlayer player, Tag payload) {
        if (!(payload instanceof CompoundTag compound)) {
            return;
        }

        MinecraftServer server = player.level().getServer();

        server.execute(() -> {
            CommandSourceStack source = player.createCommandSourceStack();

            // Das Menü ist nur für Admins; die Befehle prüfen die Rechte zusätzlich selbst.
            if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                return;
            }

            String template = compound.getStringOr(COMMAND_KEY, "");
            String command = fillPlaceholders(template, compound).strip();

            if (command.isEmpty()) {
                return;
            }

            String root = command.split(" ", 2)[0];

            if (!ALLOWED_ROOTS.contains(root)) {
                LOGGER.warn("Ignored menu action with unexpected command from {}: {}",
                        player.getPlainTextName(), command);
                return;
            }

            server.getCommands().performPrefixedCommand(source, command);
        });
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
