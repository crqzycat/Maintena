package crqzycat.maintena.freeze;

import java.util.List;

/**
 * Texte und Optionen für das Freeze-System (freeze-config.json).
 *
 * Platzhalter in den Texten für den eingefrorenen Spieler: %player%, %source%, %reason%
 * Platzhalter in den Meldungen an Staff: %player%
 */
public class FreezeConfig {

    // Title in der Bildschirmmitte, wenn jemand eingefroren wird (bzw. nach dem Re-Join)
    public String titleText = "§c§lYOU ARE FROZEN";
    public String subtitleText = "§7Do not log out! Staff will contact you.";

    // Anweisungen im Chat für den eingefrorenen Spieler (eine Zeile pro Eintrag)
    public List<String> instructions = List.of(
            "§c❄ §lYou were frozen by §f%source%§c.",
            "§7Reason: §f%reason%",
            "§7Do not log out! Join our Discord for support: §bhttps://discord.gg/yourserver"
    );

    // Wird regelmäßig in der Actionbar angezeigt
    public String actionbarText = "§c❄ You are frozen §7- do not log out";

    public String notifyUnfrozen = "§a✓ You were unfrozen. You can move again.";

    // Wird gezeigt, wenn ein eingefrorener Spieler schreiben will (nur wenn blockChat = true)
    public String deniedChat = "§c✗ You are frozen and can't chat.";

    public String defaultReason = "Suspected use of unauthorized mods.";

    // Chat (inklusive /msg, /tell, /w, /me, /say, /teammsg) für eingefrorene Spieler sperren
    public boolean blockChat = false;

    // Eingefrorene Spieler nehmen keinen Schaden
    public boolean invulnerable = true;

    // Staff benachrichtigen, wenn ein eingefrorener Spieler den Server verlässt bzw. wieder betritt
    public boolean notifyStaff = true;
    public String staffLogout = "§c⚠ §f%player% §clogged out while frozen!";
    public String staffRejoin = "§e%player% §7joined again and is still frozen.";
}
