package crqzycat.maintena.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dauer-Angaben wie "7d", "24h", "30m", "45s" oder "1d12h" (für Bans, IP-Bans und Mutes). */
public final class DurationUtil {

    // Kombinierbar in der Reihenfolge d -> h -> m -> s, z.B. 7d, 24h, 30m, 45s, 1d12h
    private static final Pattern DURATION_LIKE = Pattern.compile("^(?:\\d+[dhms])+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile(
            "^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$", Pattern.CASE_INSENSITIVE);

    private DurationUtil() {
    }

    /**
     * Sieht der Text wie eine Dauer aus (z.B. "7d", "1d12h")? Auch dann, wenn sie ungültig ist
     * (z.B. in falscher Reihenfolge wie "30m1h").
     */
    public static boolean looksLikeDuration(String token) {
        return DURATION_LIKE.matcher(token).matches();
    }

    /**
     * Wandelt "7d", "24h", "30m", "45s" oder Kombinationen wie "1d12h" in Millisekunden um.
     * Liefert null bei ungültiger Eingabe, 0 oder Überlauf.
     */
    public static Long parse(String token) {
        Matcher matcher = DURATION.matcher(token);

        if (token.isEmpty() || !matcher.matches()) {
            return null;
        }

        try {
            long days = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
            long hours = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
            long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
            long seconds = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

            long totalSeconds = Math.addExact(
                    Math.addExact(Math.multiplyExact(days, 86_400L), Math.multiplyExact(hours, 3_600L)),
                    Math.addExact(Math.multiplyExact(minutes, 60L), seconds)
            );

            long millis = Math.multiplyExact(totalSeconds, 1_000L);

            if (millis <= 0) {
                return null;
            }

            // Date-Überlauf verhindern
            Math.addExact(System.currentTimeMillis(), millis);

            return millis;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
