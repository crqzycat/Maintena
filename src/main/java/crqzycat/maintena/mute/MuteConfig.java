package crqzycat.maintena.mute;

/**
 * Texte und Optionen für das Mute-System (mute-config.json).
 *
 * Platzhalter in den Texten für den Spieler: %reason%, %source%, %remaining%, %expires%
 * Platzhalter in den Meldungen beim Mute: %reason%, %source%, %duration%
 * Platzhalter in den Broadcasts: %player%, %source%, %reason%, %duration%
 */
public class MuteConfig {

    // Wird angezeigt, wenn ein gemuteter Spieler versucht zu schreiben
    public String deniedTemporary =
            "§c✗ You are muted and can't chat. §7Reason: §f%reason% §7| Time remaining: §e%remaining%";

    public String deniedPermanent =
            "§c✗ You are permanently muted and can't chat. §7Reason: §f%reason%";

    // Wird dem Spieler beim Mute gezeigt
    public String notifyTemporary =
            "§cYou were muted by §f%source% §cfor §e%duration%§c. §7Reason: §f%reason%";

    public String notifyPermanent =
            "§cYou were permanently muted by §f%source%§c. §7Reason: §f%reason%";

    public String notifyUnmuted = "§a✓ You were unmuted. You can chat again.";
    public String notifyExpired = "§a✓ Your mute has expired. You can chat again.";

    public String defaultReason = "Muted by an operator.";

    // Optionale Meldung an alle Spieler, wenn jemand gemutet wird
    public boolean broadcastEnabled = false;
    public String broadcastPermanent = "§c%player% §7was muted by §f%source%§7: §f%reason%";
    public String broadcastTemporary = "§c%player% §7was muted by §f%source% §7for §e%duration%§7: §f%reason%";
}
