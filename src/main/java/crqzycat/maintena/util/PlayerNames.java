package crqzycat.maintena.util;

import crqzycat.maintena.maintenance.MaintenanceManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sammelt Spielernamen für Vorschläge und die Spielersuche im Menü:
 * online, alle aus usercache.json (jeder, der schon mal gejoint ist), die Maintenance-Whitelist
 * und Namen, die per Mojang-Abfrage bestätigt wurden (auch wenn der Spieler nie auf dem Server war).
 */
public final class PlayerNames {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** klein geschrieben -> exakte Schreibweise laut Mojang (nur bis zum Server-Neustart). */
    private static final Map<String, String> CONFIRMED = new ConcurrentHashMap<>();

    private PlayerNames() {
    }

    public record SearchResult(List<String> names, int total) {
    }

    /** Gültiger Minecraft-Name (3-16 Zeichen: Buchstaben, Zahlen, Unterstrich). */
    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Merkt sich einen bei Mojang bestätigten Namen (in exakter Schreibweise). */
    public static void remember(String exactName) {
        CONFIRMED.put(exactName.toLowerCase(Locale.ROOT), exactName);
    }

    /** Alle bekannten Namen, alphabetisch (ohne Doppelte, Groß-/Kleinschreibung egal). */
    public static Set<String> known() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        MaintenanceManager maintenance = MaintenanceManager.getInstance();
        names.addAll(maintenance.getAllPlayerNames());
        names.addAll(maintenance.getWhitelistedPlayers());
        names.addAll(CONFIRMED.values());

        return names;
    }

    /**
     * Sucht Namen, die den Text enthalten. Reihenfolge: Online-Spieler zuerst, dann Namen, die
     * mit dem Text beginnen, dann alphabetisch. Ein leerer Text liefert alle (Online zuerst).
     */
    public static SearchResult search(Collection<String> online, String query, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);

        Set<String> onlineLower = online.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Comparator<String> order = Comparator
                .comparingInt((String name) -> onlineLower.contains(name.toLowerCase(Locale.ROOT)) ? 0 : 1)
                .thenComparingInt(name -> name.toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1)
                .thenComparing(String.CASE_INSENSITIVE_ORDER);

        List<String> matches = known().stream()
                .filter(name -> q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q))
                .sorted(order)
                .toList();

        return new SearchResult(matches.stream().limit(limit).toList(), matches.size());
    }
}
