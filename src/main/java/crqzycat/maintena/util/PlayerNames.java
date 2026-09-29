package crqzycat.maintena.util;

import crqzycat.maintena.maintenance.MaintenanceManager;

import java.util.Set;
import java.util.TreeSet;

/**
 * Bekannte Spielernamen für die Namensvorschläge (Tab-Vervollständigung): alle, die online sind
 * oder in usercache.json stehen (also schon mal auf dem Server waren), plus die Whitelist.
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    /** Alle bekannten Namen, alphabetisch (ohne Doppelte, Groß-/Kleinschreibung egal). */
    public static Set<String> known() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        MaintenanceManager maintenance = MaintenanceManager.getInstance();
        names.addAll(maintenance.getAllPlayerNames());
        names.addAll(maintenance.getWhitelistedPlayers());

        return names;
    }
}
