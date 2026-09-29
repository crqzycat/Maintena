package crqzycat.maintena.ban;

/**
 * Texte und Optionen für das Ban-System (ban-config.json).
 *
 * Platzhalter in den Ban-Screens: %reason%, %source%, %created%, %expires%, %remaining%
 * Platzhalter in den Broadcasts: %player%, %source%, %reason%, %duration%
 */
public class BanConfig {

    // Wird beim Login-Versuch eines gebannten Spielers und beim Kick eines Online-Spielers angezeigt
    public String banScreenTemporary =
            "§c§lYou are banned from this server.\n\n"
                    + "§7Reason: §f%reason%\n"
                    + "§7Banned by: §f%source%\n"
                    + "§7Banned on: §f%created%\n"
                    + "§7Expires: §f%expires%\n"
                    + "§7Time remaining: §e%remaining%";

    public String banScreenPermanent =
            "§c§lYou are permanently banned from this server.\n\n"
                    + "§7Reason: §f%reason%\n"
                    + "§7Banned by: §f%source%\n"
                    + "§7Banned on: §f%created%";

    public String defaultReason = "Banned by an operator.";

    // java.time Format-Muster, Zeitzone ist die des Servers
    public String dateFormat = "yyyy-MM-dd HH:mm:ss z";

    // Optionale Meldung an alle Spieler, wenn jemand gebannt wird
    public boolean broadcastEnabled = false;
    public String broadcastPermanent = "§c%player% §7was banned by §f%source%§7: §f%reason%";
    public String broadcastTemporary = "§c%player% §7was banned by §f%source% §7for §e%duration%§7: §f%reason%";
}
