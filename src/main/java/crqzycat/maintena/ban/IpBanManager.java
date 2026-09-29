package crqzycat.maintena.ban;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IP-Ban-System auf Basis der Vanilla-Banliste (banned-ips.json), genau wie BanManager
 * für Namen. Ban-Screen-Texte, Dauer-Parsing und Datumsformatierung kommen aus BanConfig
 * bzw. BanManager, damit beide Ban-Arten konsistent aussehen.
 */
public class IpBanManager {

    private static IpBanManager instance;

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    public static IpBanManager getInstance() {
        if (instance == null) {
            instance = new IpBanManager();
        }

        return instance;
    }

    private IpBanManager() {
    }

    public static boolean isIpAddress(String token) {
        return IPV4.matcher(token).matches();
    }

    /**
     * Liest die aktuelle IP-Adresse eines online Spielers direkt aus seiner Verbindung aus,
     * da PlayerList in dieser Version keine öffentliche Hilfsmethode dafür anbietet.
     */
    public static String getIpAddress(ServerPlayer player) {
        SocketAddress address = player.connection.getConnection().getRemoteAddress();

        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }

        return address.toString();
    }

    /**
     * Ermittelt die IP eines Ziels: entweder eine direkt angegebene IP-Adresse,
     * oder der aktuell verbundene Spieler mit diesem Namen.
     */
    public String resolveIp(MinecraftServer server, String target) {
        if (isIpAddress(target)) {
            return target;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(target);
        return player != null ? getIpAddress(player) : null;
    }

    public static boolean isExpired(IpBanListEntry entry) {
        Date expires = entry.getExpires();
        return expires != null && expires.getTime() <= System.currentTimeMillis();
    }

    /**
     * Bannt eine IP-Adresse (permanent oder auf Zeit). Ein bestehender Ban wird überschrieben.
     *
     * @param durationMillis Dauer in Millisekunden, null = permanent
     * @param reason         Grund, null = Standardgrund aus der Ban-Config
     * @return true, wenn die IP vorher schon gebannt war (Ban wurde aktualisiert)
     */
    public boolean ban(MinecraftServer server, String ip, Long durationMillis, String reason, String source) {
        IpBanList bans = server.getPlayerList().getIpBans();
        boolean existed = bans.isBanned(ip);

        BanConfig config = BanManager.getInstance().getConfig();

        Date created = new Date();
        Date expires = durationMillis == null ? null : new Date(created.getTime() + durationMillis);
        String finalReason = (reason == null || reason.isBlank()) ? config.defaultReason : reason;

        IpBanListEntry entry = new IpBanListEntry(ip, created, source, expires, finalReason);
        bans.add(entry);

        Component screen = BanManager.getInstance().buildBanScreen(finalReason, source, created, expires);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ip.equals(getIpAddress(player))) {
                player.connection.disconnect(screen);
            }
        }

        return existed;
    }

    public IpBanListEntry findBan(MinecraftServer server, String ip) {
        for (IpBanListEntry entry : server.getPlayerList().getIpBans().getEntries()) {
            if (!isExpired(entry) && entry.getUser().equalsIgnoreCase(ip)) {
                return entry;
            }
        }

        return null;
    }

    public boolean unban(MinecraftServer server, String ip) {
        IpBanListEntry entry = findBan(server, ip);

        if (entry == null) {
            return false;
        }

        server.getPlayerList().getIpBans().remove(entry.getUser());
        return true;
    }

    public List<IpBanListEntry> getActiveBans(MinecraftServer server) {
        List<IpBanListEntry> active = new ArrayList<>();

        for (IpBanListEntry entry : server.getPlayerList().getIpBans().getEntries()) {
            if (!isExpired(entry)) {
                active.add(entry);
            }
        }

        active.sort(Comparator.comparing(IpBanListEntry::getUser));
        return active;
    }

    public String describeRemaining(IpBanListEntry entry) {
        Date expires = entry.getExpires();

        if (expires == null) {
            return "permanent";
        }

        return BanManager.formatDuration(expires.getTime() - System.currentTimeMillis());
    }
}
