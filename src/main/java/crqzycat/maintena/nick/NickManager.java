package crqzycat.maintena.nick;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.util.PlayerNames;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Nicknames: Staff ändert den Namen, den andere Spieler sehen (Name über dem Kopf, Tab-Liste,
 * Chat), ohne den echten Account-Namen zu ändern.
 *
 * Technisch ist ein Nickname eine Identität mit dem echten Skin und dem neuen Namen; die
 * Umsetzung (Tab-Eintrag tauschen, Entity neu senden, Anzeigename) übernimmt der
 * {@link DisguiseManager}. Nickname und Disguise teilen sich deshalb einen Identitäts-Slot:
 * der neuere ersetzt den älteren.
 *
 * Der Zustand liegt nur im Arbeitsspeicher: Ein Nickname endet beim Disconnect und beim
 * Server-Neustart. Ein Tod beendet ihn nicht (er wird nach dem Respawn wieder gesetzt).
 *
 * Erlaubt sind Namen wie bei Minecraft-Accounts: 3-16 Zeichen, Buchstaben, Ziffern und _.
 * Farbcodes sind bewusst nicht möglich: Der Name steckt im Profil, das der Client
 * ausschließlich als normalen Spielernamen akzeptiert.
 */
public final class NickManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final NickManager INSTANCE = new NickManager();

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]+");

    private NickManager() {
    }

    public static NickManager getInstance() {
        return INSTANCE;
    }

    /**
     * Prüft einen gewünschten Nickname.
     *
     * @return eine Fehlermeldung (ohne Farbcode) oder null, wenn der Name erlaubt ist
     */
    public String validate(ServerPlayer player, String nick) {
        if (nick == null || nick.isEmpty()) {
            return "Please enter a nickname";
        }

        if (nick.chars().anyMatch(Character::isWhitespace)) {
            return "A nickname can't contain spaces";
        }

        if (nick.length() < MIN_LENGTH || nick.length() > MAX_LENGTH) {
            return "A nickname needs " + MIN_LENGTH + "-" + MAX_LENGTH + " characters";
        }

        if (!VALID.matcher(nick).matches()) {
            return "Only letters, digits and _ are allowed (no color codes)";
        }

        if (nick.equalsIgnoreCase(player.getGameProfile().name())) {
            return "That is your real name, use /nick reset instead";
        }

        DisguiseManager manager = DisguiseManager.getInstance();

        if (nick.equals(manager.getNickname(player))) {
            return "You already use this nickname";
        }

        // Namen echter Spieler: online, schon mal auf dem Server gewesen oder auf der Whitelist
        if (player.level().getServer().getPlayerList().getPlayerByName(nick) != null
                || PlayerNames.known().contains(nick)) {
            return "That name belongs to a player on this server";
        }

        // Namen, die gerade ein anderer Nickname oder eine Spieler-Disguise benutzt
        if (manager.isIdentityNameTaken(nick, player.getUUID())) {
            return "That name is already in use by another nickname or disguise";
        }

        return null;
    }

    /** Setzt den Nickname (einen vorhandenen Nickname oder eine Disguise ersetzt er). */
    public boolean set(ServerPlayer player, String nick) {
        if (!DisguiseManager.getInstance().nickname(player, nick)) {
            return false;
        }

        LOGGER.info("[Nick] {} set the nickname {}", player.getName().getString(), nick);
        return true;
    }

    /** Entfernt den Nickname. @return false, wenn der Spieler keinen hat */
    public boolean reset(ServerPlayer player) {
        String old = DisguiseManager.getInstance().getNickname(player);

        if (!DisguiseManager.getInstance().clearNickname(player)) {
            return false;
        }

        LOGGER.info("[Nick] {} removed the nickname {}", player.getName().getString(), old);
        return true;
    }

    /** Aktueller Nickname des Spielers oder null. */
    public String get(ServerPlayer player) {
        return DisguiseManager.getInstance().getNickname(player);
    }

    /** Spieler-UUID -> Nickname aller aktuellen Nicknames. */
    public Map<UUID, String> all() {
        return DisguiseManager.getInstance().nicknames();
    }
}
