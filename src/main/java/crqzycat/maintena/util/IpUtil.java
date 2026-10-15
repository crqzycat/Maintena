package crqzycat.maintena.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/** IP-Adressen (IPv4 und IPv6) prüfen und in eine einheitliche Schreibweise bringen. */
public final class IpUtil {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private IpUtil() {
    }

    /**
     * Bringt eine IP-Adresse in die Schreibweise, in der der Server sie sieht (IPv4 wie
     * eingegeben, IPv6 ausgeschrieben in Kleinbuchstaben, ohne Zonen-Zusatz). So passen
     * "2001:db8::1" und "2001:db8:0:0:0:0:0:1" zur selben Ban-Liste.
     *
     * @return die einheitliche Schreibweise, oder null, wenn der Text keine IP-Adresse ist
     */
    public static String normalize(String token) {
        if (token == null) {
            return null;
        }

        String value = token.trim();

        if (IPV4.matcher(value).matches()) {
            return value;
        }

        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }

        int scope = value.indexOf('%');

        if (scope >= 0) {
            value = value.substring(0, scope);
        }

        if (!value.contains(":") || !IPV6_CHARS.matcher(value).matches()) {
            return null;
        }

        try {
            // In eckigen Klammern wird garantiert nur als IPv6-Adresse gelesen, nie per DNS aufgelöst
            InetAddress address = InetAddress.getByName("[" + value + "]");

            // IPv4-mapped (::ffff:1.2.3.4) liefert Java als normale IPv4-Adresse
            return hostAddress(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** Ist der Text eine gültige IPv4- oder IPv6-Adresse? */
    public static boolean isIpAddress(String token) {
        return normalize(token) != null;
    }

    /** Adresse als Text, ohne den Zonen-Zusatz (%eth0), den Java bei Link-Local-IPv6 anhängt. */
    public static String hostAddress(InetAddress address) {
        String text = address.getHostAddress();
        int scope = text.indexOf('%');

        return scope >= 0 ? text.substring(0, scope) : text;
    }
}
