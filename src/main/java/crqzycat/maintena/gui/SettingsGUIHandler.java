package crqzycat.maintena.gui;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
