package crqzycat.maintena.mute;

import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Chat-Timeouts: gemutete Spieler können nicht mehr schreiben (Chat, /msg, /me, ...),
 * alle anderen Befehle funktionieren weiter. Mutes sind permanent oder auf Zeit und
 * werden in mutes.json gespeichert (überleben also Neustarts).
 */
public class MuteManager {

    private static MuteManager instance;

    private MuteData data;
    private MuteConfig config;

    private MuteManager() {
        reload();
    }

    public static synchronized MuteManager getInstance() {
        if (instance == null) {
            instance = new MuteManager();
        }

        return instance;
    }

    public synchronized void reload() {
        this.data = PersistenceUtil.loadMuteData();
        this.config = PersistenceUtil.loadMuteConfig();
    }

    public MuteConfig getConfig() {
        return config;
    }

    private void save() {
        PersistenceUtil.saveMuteData(data);
    }

    // ==================== Mutes ====================

    /**
     * Mutet einen Spieler (permanent oder auf Zeit). Ein bestehender Mute wird überschrieben.
     *
     * @param durationMillis Dauer in Millisekunden, null = permanent
     * @param reason         Grund, null = Standardgrund aus der Config
     * @return true, wenn der Spieler vorher schon gemutet war (Mute wurde aktualisiert)
     */
    public synchronized boolean mute(
            MinecraftServer server,
            NameAndId target,
            Long durationMillis,
            String reason,
            String source
    ) {
        String uuid = target.id().toString();
        boolean existed = data.mutes.removeIf(entry -> uuid.equals(entry.uuid) && !entry.isExpired());
        data.mutes.removeIf(entry -> uuid.equals(entry.uuid));

        long now = System.currentTimeMillis();

        MuteData.Entry entry = new MuteData.Entry();
        entry.uuid = uuid;
        entry.name = target.name();
        entry.source = source;
        entry.reason = (reason == null || reason.isBlank()) ? config.defaultReason : reason;
        entry.created = now;
        entry.expires = durationMillis == null ? 0 : now + durationMillis;

        data.mutes.add(entry);
        save();

        String duration = durationMillis == null ? "" : BanManager.formatDuration(durationMillis);

        ServerPlayer online = server.getPlayerList().getPlayer(target.id());
        if (online != null) {
            String template = durationMillis == null ? config.notifyPermanent : config.notifyTemporary;
            online.sendSystemMessage(Component.literal(template
                    .replace("%source%", source)
                    .replace("%reason%", entry.reason)
                    .replace("%duration%", duration)));
        }

        if (config.broadcastEnabled) {
            String template = durationMillis == null ? config.broadcastPermanent : config.broadcastTemporary;
            Component message = Component.literal(template
                    .replace("%player%", target.name())
                    .replace("%source%", source)
                    .replace("%reason%", entry.reason)
                    .replace("%duration%", duration));

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        }

        return existed;
    }

    /**
     * Hebt den Mute eines Spielers auf (Name, Groß-/Kleinschreibung egal).
     */
    public synchronized boolean unmute(MinecraftServer server, String name) {
        MuteData.Entry entry = findByName(name);

        if (entry == null) {
            return false;
        }

        data.mutes.remove(entry);
        save();

        try {
            ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(entry.uuid));
            if (online != null) {
                online.sendSystemMessage(Component.literal(config.notifyUnmuted));
            }
        } catch (IllegalArgumentException ignored) {
            // kaputte UUID in der Datei: Mute ist trotzdem entfernt
        }

        return true;
    }

    /** Aktiver Mute eines Spielers, oder null. */
    public synchronized MuteData.Entry find(UUID uuid) {
        String id = uuid.toString();

        for (MuteData.Entry entry : data.mutes) {
            if (id.equals(entry.uuid) && !entry.isExpired()) {
                return entry;
            }
        }

        return null;
    }

    public synchronized MuteData.Entry findByName(String name) {
        for (MuteData.Entry entry : data.mutes) {
            if (!entry.isExpired() && entry.name != null && entry.name.equalsIgnoreCase(name)) {
                return entry;
            }
        }

        return null;
    }

    /** Alle aktiven (nicht abgelaufenen) Mutes, alphabetisch nach Spielername. */
    public synchronized List<MuteData.Entry> getActiveMutes() {
        List<MuteData.Entry> active = new ArrayList<>();

        for (MuteData.Entry entry : data.mutes) {
            if (!entry.isExpired()) {
                active.add(entry);
            }
        }

        active.sort(Comparator.comparing(entry -> String.valueOf(entry.name).toLowerCase()));
        return active;
    }

    /**
     * Entfernt abgelaufene Mutes und sagt online Spielern Bescheid, dass sie wieder schreiben
     * dürfen. Wird einmal pro Sekunde vom Server-Tick aufgerufen.
     */
    public synchronized void tick(MinecraftServer server) {
        boolean changed = false;

        Iterator<MuteData.Entry> iterator = data.mutes.iterator();

        while (iterator.hasNext()) {
            MuteData.Entry entry = iterator.next();

            if (!entry.isExpired()) {
                continue;
            }

            iterator.remove();
            changed = true;

            try {
                ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(entry.uuid));
                if (online != null) {
                    online.sendSystemMessage(Component.literal(config.notifyExpired));
                }
            } catch (IllegalArgumentException ignored) {
                // kaputte UUID: einfach entfernen
            }
        }

        if (changed) {
            save();
        }
    }

    // ==================== Anzeige ====================

    /** Meldung an einen gemuteten Spieler, der gerade schreiben wollte. */
    public Component buildDeniedMessage(MuteData.Entry entry) {
        String template = entry.isPermanent() ? config.deniedPermanent : config.deniedTemporary;

        return Component.literal(template
                .replace("%reason%", entry.reason != null ? entry.reason : config.defaultReason)
                .replace("%source%", entry.source != null ? entry.source : "-")
                .replace("%remaining%", describeRemaining(entry))
                .replace("%expires%", entry.isPermanent()
                        ? "never"
                        : BanManager.getInstance().formatDate(new java.util.Date(entry.expires))));
    }

    public String describeRemaining(MuteData.Entry entry) {
        return entry.isPermanent() ? "permanent" : BanManager.formatDuration(entry.remainingMillis());
    }
}
