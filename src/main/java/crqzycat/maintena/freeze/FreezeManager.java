package crqzycat.maintena.freeze;

import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.util.PersistenceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freeze: eingefrorene Spieler können sich nicht mehr bewegen und nicht mehr mit der Welt
 * interagieren (Blöcke, Items, Entities); Chat ist optional gesperrt. Gedacht für Spieler, die
 * verdächtigt werden, unerlaubte Mods zu benutzen.
 *
 * Der Zustand liegt nur im Arbeitsspeicher: er bleibt beim Re-Join erhalten (Ausloggen befreit
 * also nicht), geht aber bei einem Server-Neustart verloren.
 *
 * Bewegung: MaintenaFreezeMoveMixin verwirft die Positionspakete des Clients, hier wird der
 * Spieler bei Bewegungsversuchen zurückgesetzt. Interaktionen, Chat und Schaden blockt FreezeHandler.
 */
public final class FreezeManager {

    private static FreezeManager instance;

    /** Ein eingefrorener Spieler (auch offline, solange er eingefroren bleibt). */
    public static final class Entry {
        public UUID id;
        public String name;
        public String source;
        public String reason;
        public long since;

        private volatile ResourceKey<Level> dimension;
        private volatile double x;
        private volatile double y;
        private volatile double z;

        /** Merkt sich die aktuelle Position des Spielers als Freeze-Punkt. */
        void capture(ServerPlayer player) {
            this.dimension = player.level().dimension();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    /** Eingefrorene Spieler, die versucht haben, sich zu bewegen (werden im nächsten Tick zurückgesetzt). */
    private final Set<UUID> moveAttempts = ConcurrentHashMap.newKeySet();

    private FreezeConfig config;

    private FreezeManager() {
        reload();
    }

    public static synchronized FreezeManager getInstance() {
        if (instance == null) {
            instance = new FreezeManager();
        }

        return instance;
    }

    /** Lädt nur die Config neu; wer eingefroren ist, bleibt eingefroren. */
    public synchronized void reload() {
        this.config = PersistenceUtil.loadFreezeConfig();
    }

    public FreezeConfig getConfig() {
        return config;
    }

    // ==================== Status ====================

    public boolean isFrozen(UUID id) {
        return !entries.isEmpty() && entries.containsKey(id);
    }

    public boolean isFrozen(ServerPlayer player) {
        return isFrozen(player.getUUID());
    }

    public Entry find(UUID id) {
        return entries.get(id);
    }

    public Entry findByName(String name) {
        for (Entry entry : entries.values()) {
            if (entry.name != null && entry.name.equalsIgnoreCase(name)) {
                return entry;
            }
        }

        return null;
    }

    /** Alle eingefrorenen Spieler (auch offline), alphabetisch. */
    public List<Entry> getFrozen() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(entry -> String.valueOf(entry.name).toLowerCase()));
        return list;
    }

    // ==================== Freeze / Unfreeze ====================

    /**
     * Friert einen (online befindlichen) Spieler an seiner aktuellen Position ein.
     *
     * @param reason Grund, null/leer = Standardgrund aus der Config
     * @return false, wenn der Spieler schon eingefroren ist
     */
    public boolean freeze(ServerPlayer target, String source, String reason) {
        UUID id = target.getUUID();

        if (entries.containsKey(id)) {
            return false;
        }

        // Aus Fahrzeugen aussteigen, damit der Spieler nicht weiterfährt
        target.stopRiding();

        Entry entry = new Entry();
        entry.id = id;
        entry.name = target.nameAndId().name();
        entry.source = source;
        entry.reason = (reason == null || reason.isBlank()) ? config.defaultReason : reason;
        entry.since = System.currentTimeMillis();
        entry.capture(target);

        entries.put(id, entry);
        sendInstructions(target, entry);

        return true;
    }

    /** Hebt das Einfrieren auf (Name, Groß-/Kleinschreibung egal; auch für Offline-Spieler). */
    public boolean unfreeze(MinecraftServer server, String name) {
        Entry entry = findByName(name);

        if (entry == null) {
            return false;
        }

        release(server, entry);
        return true;
    }

    /** Hebt das Einfrieren aller Spieler auf. */
    public int unfreezeAll(MinecraftServer server) {
        int count = 0;

        for (Entry entry : new ArrayList<>(entries.values())) {
            release(server, entry);
            count++;
        }

        return count;
    }

    private void release(MinecraftServer server, Entry entry) {
        entries.remove(entry.id);
        moveAttempts.remove(entry.id);

        ServerPlayer online = server.getPlayerList().getPlayer(entry.id);

        if (online != null) {
            online.connection.send(new ClientboundClearTitlesPacket(true));
            online.sendSystemMessage(Component.literal(config.notifyUnfrozen));
            online.sendSystemMessage(Component.empty(), true); // Actionbar leeren
        }
    }

    // ==================== Events ====================

    /** Der Client hat ein Positionspaket geschickt (vom Mixin verworfen): merken, falls er sich wegbewegt hat. */
    public void onMoveAttempt(ServerPlayer player, double x, double y, double z) {
        Entry entry = entries.get(player.getUUID());

        if (entry == null) {
            return;
        }

        double dx = x - entry.x;
        double dy = y - entry.y;
        double dz = z - entry.z;

        if (dx * dx + dy * dy + dz * dz > 1.0E-6) {
            moveAttempts.add(entry.id);
        }
    }

    /** Spieler ist (wieder) auf dem Server: Freeze-Punkt neu setzen und Anweisungen erneut zeigen. */
    public void onJoin(ServerPlayer player) {
        Entry entry = entries.get(player.getUUID());

        if (entry == null) {
            return;
        }

        entry.capture(player);
        sendInstructions(player, entry);

        if (config.notifyStaff) {
            notifyStaff(player, config.staffRejoin.replace("%player%", entry.name));
        }
    }

    public void onDisconnect(ServerPlayer player) {
        Entry entry = entries.get(player.getUUID());

        if (entry == null) {
            return;
        }

        moveAttempts.remove(entry.id);

        if (config.notifyStaff) {
            notifyStaff(player, config.staffLogout.replace("%player%", entry.name));
        }
    }

    /** Einmal pro Tick: Spieler, die sich wegbewegen wollten, zurücksetzen. */
    public void tick(MinecraftServer server) {
        if (entries.isEmpty()) {
            return;
        }

        for (Entry entry : entries.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.id);

            if (player == null) {
                continue;
            }

            double dx = player.getX() - entry.x;
            double dy = player.getY() - entry.y;
            double dz = player.getZ() - entry.z;

            boolean displaced = !player.level().dimension().equals(entry.dimension)
                    || dx * dx + dy * dy + dz * dz > 1.0E-6;

            if (displaced) {
                // Der Server selbst hat den Spieler bewegt (/tp, Respawn, ...): neuer Freeze-Punkt
                entry.capture(player);
                moveAttempts.remove(entry.id);
            } else if (moveAttempts.remove(entry.id)) {
                player.connection.teleport(entry.x, entry.y, entry.z, player.getYRot(), player.getXRot());
            }
        }
    }

    /** Erinnerung in der Actionbar (wird alle paar Sekunden vom Handler aufgerufen). */
    public void sendReminders(MinecraftServer server) {
        if (entries.isEmpty()) {
            return;
        }

        Component text = Component.literal(config.actionbarText);

        for (Entry entry : entries.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.id);

            if (player != null) {
                player.sendSystemMessage(text, true);
            }
        }
    }

    // ==================== Anzeige ====================

    private void sendInstructions(ServerPlayer player, Entry entry) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(fill(config.subtitleText, entry))));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(fill(config.titleText, entry))));

        for (String line : config.instructions) {
            player.sendSystemMessage(Component.literal(fill(line, entry)));
        }
    }

    private static String fill(String template, Entry entry) {
        return template
                .replace("%player%", String.valueOf(entry.name))
                .replace("%source%", String.valueOf(entry.source))
                .replace("%reason%", String.valueOf(entry.reason));
    }

    /** Meldung an alle Operatoren (außer dem betroffenen Spieler selbst). */
    private void notifyStaff(ServerPlayer except, String message) {
        MinecraftServer server = except.level().getServer();
        Component text = Component.literal(message);

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != except && other.createCommandSourceStack().permissions()
                    .hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                other.sendSystemMessage(text);
            }
        }
    }

    public String describeSince(Entry entry) {
        return BanManager.getInstance().formatDate(new java.util.Date(entry.since));
    }
}
