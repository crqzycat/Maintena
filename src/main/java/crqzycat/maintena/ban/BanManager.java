package crqzycat.maintena.ban;

import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ban-System auf Basis der Vanilla-Banliste (banned-players.json).
 * Maintena legt keine eigene Banliste an, sondern trägt Bans mit Ablaufdatum in die
 * vorhandene ein. Dadurch bleiben Vanilla-Bans, /pardon und andere Mods kompatibel.
 */
public class BanManager {

    private static BanManager instance;

    private BanConfig config;

    // Kombinierbar in der Reihenfolge d -> h -> m, z.B. 7d, 24h, 30m, 1d12h
    private static final Pattern DURATION_LIKE = Pattern.compile("^(?:\\d+[dhm])+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile(
            "^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?$", Pattern.CASE_INSENSITIVE);

    private BanManager() {
        this.config = PersistenceUtil.loadBanConfig();
    }

    public static BanManager getInstance() {
        if (instance == null) {
            instance = new BanManager();
        }

        return instance;
    }

    public void reload() {
        this.config = PersistenceUtil.loadBanConfig();
    }

    public BanConfig getConfig() {
        return config;
    }

    // ==================== Dauer ====================

    /**
     * Sieht der Text wie eine Dauer aus (z.B. "7d", "1d12h")? Auch dann, wenn sie ungültig ist.
     */
    public static boolean looksLikeDuration(String token) {
        return DURATION_LIKE.matcher(token).matches();
    }

    /**
     * Wandelt "7d", "24h", "30m" oder Kombinationen wie "1d12h" in Millisekunden um.
     * Liefert null bei ungültiger Eingabe, 0 oder Überlauf.
     */
    public static Long parseDuration(String token) {
        Matcher matcher = DURATION.matcher(token);

        if (token.isEmpty() || !matcher.matches()) {
            return null;
        }

        try {
            long days = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
            long hours = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
            long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;

            long totalMinutes = Math.addExact(
                    Math.addExact(Math.multiplyExact(days, 1440L), Math.multiplyExact(hours, 60L)),
                    minutes
            );

            long millis = Math.multiplyExact(totalMinutes, 60_000L);

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

    /**
     * Sekundengenaue Anzeige, z.B. "6d 23h 59m 12s".
     */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);

        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (seconds > 0) parts.add(seconds + "s");

        return parts.isEmpty() ? "0s" : String.join(" ", parts);
    }

    public String formatDate(Date date) {
        if (date == null) {
            return "-";
        }

        DateTimeFormatter formatter;

        try {
            formatter = DateTimeFormatter.ofPattern(config.dateFormat);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        }

        return formatter.format(Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()));
    }

    // ==================== Bans ====================

    public static boolean isExpired(UserBanListEntry entry) {
        Date expires = entry.getExpires();
        return expires != null && expires.getTime() <= System.currentTimeMillis();
    }

    /**
     * Bannt einen Spieler (permanent oder auf Zeit). Ein bestehender Ban wird überschrieben.
     *
     * @param durationMillis Dauer in Millisekunden, null = permanent
     * @param reason         Grund, null = Standardgrund aus der Config
     * @return true, wenn der Spieler vorher schon gebannt war (Ban wurde aktualisiert)
     */
    public boolean ban(
            MinecraftServer server,
            NameAndId target,
            Long durationMillis,
            String reason,
            String source
    ) {
        UserBanList bans = server.getPlayerList().getBans();
        boolean existed = bans.isBanned(target);

        Date created = new Date();
        Date expires = durationMillis == null ? null : new Date(created.getTime() + durationMillis);
        String finalReason = (reason == null || reason.isBlank()) ? config.defaultReason : reason;

        UserBanListEntry entry = new UserBanListEntry(target, created, source, expires, finalReason);
        bans.add(entry);

        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            online.connection.disconnect(buildBanScreen(entry));
        }

        if (config.broadcastEnabled) {
            String template = durationMillis == null ? config.broadcastPermanent : config.broadcastTemporary;
            String text = template
                    .replace("%player%", target.name())
                    .replace("%source%", source)
                    .replace("%reason%", finalReason)
                    .replace("%duration%", durationMillis == null ? "" : formatDuration(durationMillis));

            Component message = Component.literal(text);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        }

        return existed;
    }

    /**
     * Sucht einen aktiven Ban anhand des Spielernamens (Groß-/Kleinschreibung egal).
     */
    public UserBanListEntry findBan(MinecraftServer server, String name) {
        for (UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
            if (!isExpired(entry) && entry.getUser().name().equalsIgnoreCase(name)) {
                return entry;
            }
        }

        return null;
    }

    public boolean unban(MinecraftServer server, String name) {
        UserBanListEntry entry = findBan(server, name);

        if (entry == null) {
            return false;
        }

        server.getPlayerList().getBans().remove(entry.getUser());
        return true;
    }

    /**
     * Alle aktiven (nicht abgelaufenen) Bans, alphabetisch nach Spielername.
     */
    public List<UserBanListEntry> getActiveBans(MinecraftServer server) {
        List<UserBanListEntry> active = new ArrayList<>();

        for (UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
            if (!isExpired(entry)) {
                active.add(entry);
            }
        }

        active.sort(Comparator.comparing(entry -> entry.getUser().name().toLowerCase()));
        return active;
    }

    // ==================== Anzeige ====================

    /**
     * Baut den Bildschirm, den ein gebannter Spieler sieht (Login-Versuch oder Kick).
     * Die Restzeit ist zum Zeitpunkt des Aufrufs sekundengenau.
     */
    public Component buildBanScreen(UserBanListEntry entry) {
        Date expires = entry.getExpires();
        String template = expires == null ? config.banScreenPermanent : config.banScreenTemporary;

        String reason = entry.getReason() != null ? entry.getReason() : config.defaultReason;
        String source = entry.getSource() != null ? entry.getSource() : "-";

        String text = template
                .replace("%reason%", reason)
                .replace("%source%", source)
                .replace("%created%", formatDate(entry.getCreated()))
                .replace("%expires%", formatDate(expires))
                .replace("%remaining%", expires == null
                        ? "permanent"
                        : formatDuration(expires.getTime() - System.currentTimeMillis()));

        return Component.literal(text);
    }

    public String describeRemaining(UserBanListEntry entry) {
        Date expires = entry.getExpires();

        if (expires == null) {
            return "permanent";
        }

        return formatDuration(expires.getTime() - System.currentTimeMillis());
    }
}
