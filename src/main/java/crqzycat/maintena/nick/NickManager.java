package crqzycat.maintena.nick;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.util.PlayerNames;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Nicknames: Staff ändert den Namen, den andere Spieler sehen (über dem Kopf, Tab-Liste, Chat).
 * Technisch eine Identität mit dem echten Skin und neuem Namen, umgesetzt im DisguiseManager
 * (Nickname und Disguise ersetzen sich gegenseitig). Nicht persistent: endet bei Disconnect,
 * Tod und Server-Neustart.
 */
public final class NickManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final NickManager INSTANCE = new NickManager();

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private NickManager() {
    }

    public static NickManager getInstance() {
        return INSTANCE;
    }

    /** @return Fehlermeldung oder null, wenn der Name erlaubt ist */
    public String validate(ServerPlayer player, String nick) {
        if (!VALID.matcher(nick).matches()) {
            return "A nickname needs 3-16 characters: letters, digits and _";
        }

        if (player.level().getServer().getPlayerList().getPlayerByName(nick) != null
                || PlayerNames.known().contains(nick)) {
            return "That name belongs to a player on this server";
        }

        return null;
    }

    public boolean set(ServerPlayer player, String nick) {
        if (!DisguiseManager.getInstance().nickname(player, nick)) {
            return false;
        }

        LOGGER.info("[Nick] {} set the nickname {}", player.getName().getString(), nick);
        return true;
    }

    public boolean reset(ServerPlayer player) {
        String old = DisguiseManager.getInstance().getNickname(player);

        if (!DisguiseManager.getInstance().clearNickname(player)) {
            return false;
        }

        LOGGER.info("[Nick] {} removed the nickname {}", player.getName().getString(), old);
        return true;
    }

    public String get(ServerPlayer player) {
        return DisguiseManager.getInstance().getNickname(player);
    }
}
