package crqzycat.maintena.gui;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import crqzycat.maintena.alias.AliasManager;
import crqzycat.maintena.nick.NickManager;
import crqzycat.maintena.vanish.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dialog.Dialog;
import java.util.Map;

/**
 * Dialog-Seiten für Vanish, Disguise, Nicknames und Aliase.
 */
final class PlayerPages {

    private PlayerPages() {
    }

    // ==================== Vanish ====================

    static Dialog vanish(CommandSourceStack a) {
        ServerPlayer self = a.getPlayer();
        boolean vanished = self != null && VanishManager.getInstance().isVanished(self);
        int others = VanishManager.getInstance().getOnlineVanished(a.getServer()).size();

        return new DialogBuilder("§6Vanish", Page.VANISH, null)
                .text(vanished ? "§bYou are vanished." : "§7You are visible.")
                .text("§7Vanished players are hidden from regular players (world and tab list). "
                        + "Operators can still see them. Currently vanished: " + others)
                .button("Vanish", "vanish on")
                .button("Become visible", "vanish off")
                .button("Show vanished players (chat)", "vanish list")
                .back(Page.MAIN)
                .build();
    }

    // ==================== Disguise ====================

    static Dialog disguise(CommandSourceStack a) {
        ServerPlayer self = a.getPlayer();
        DisguiseManager manager = DisguiseManager.getInstance();
        String status = "§7You are not disguised.";

        if (self != null) {
            String playerName = manager.getPlayerDisguiseName(self);
            EntityType<?> type = manager.getDisguiseType(self);

            if (playerName != null) {
                status = "§bYou are disguised as player §f" + playerName;
            } else if (type != null) {
                status = "§bYou are disguised as §f" + type.getDescription().getString();
            }
        }

        int total = manager.describeAll().size();

        return new DialogBuilder("§6Disguise", Page.DISGUISE, null)
                .text(status)
                .text("§7Enter a player name (any Minecraft account) or an entity id, e.g. Notch or zombie. "
                        + "Use player:<name> if a name equals an entity id. Currently disguised: " + total)
                .textInput("target", "Player or entity", "", 48)
                .button("Disguise", "disguise $(target)")
                .button("Remove disguise", "undisguise")
                .button("Show my disguise (chat)", "disguise status")
                .button("Show disguised players (chat)", "disguise list")
                .open("Refresh", Page.DISGUISE)
                .back(Page.MAIN)
                .build();
    }

    // ==================== Nicknames ====================

    static Dialog nick(CommandSourceStack a) {
        ServerPlayer self = a.getPlayer();
        NickManager manager = NickManager.getInstance();
        String current = self == null ? null : manager.get(self);

        return new DialogBuilder("§6Nicknames", Page.NICK, null)
                .text(current != null ? "§bYour nickname: §f" + current : "§7You have no nickname.")
                .text("§7Other players see the nickname instead of your name (above your head, in the tab list "
                        + "and in chat). 3-16 characters: letters, digits and _. It is removed when you disconnect "
                        + "or the server restarts.")
                .textInput("nickname", "Nickname", "", 16)
                .button("Set nickname", "nick $(nickname)")
                .button("Remove nickname", "unnick")
                .button("Show my nickname (chat)", "nick status")
                .button("Show nicknames (chat)", "nick list")
                .back(Page.MAIN)
                .build();
    }

    // ==================== Aliases ====================

    static Dialog alias() {
        int count = AliasManager.getInstance().all().size();

        return new DialogBuilder("§6Command aliases", Page.ALIAS, null)
                .text("§7Give a command a second name. Command tp and alias baum makes /baum work like /tp, "
                        + "with all its tab completions. With a sub-command, command gamemode, sub-command creative "
                        + "and alias gmc makes /gmc work like /gamemode creative. Aliases stay after restarts. "
                        + "Currently: " + count)
                .textInput("command", "Command (e.g. tp or gamemode)", "", 32)
                .textInput("sub", "Sub-command (only for the second button)", "", 64)
                .textInput("alias", "Alias (e.g. baum or gmc)", "", 32)
                .button("Create alias", "alias $(command) $(alias)")
                .button("Create alias for sub-command", "alias sub $(command) \"$(sub)\" $(alias)")
                .open("Aliases (" + count + ")", Page.ALIAS_LIST)
                .button("Show aliases (chat)", "alias list")
                .back(Page.MAIN)
                .build();
    }

    static Dialog aliasList() {
        Map<String, String> all = AliasManager.getInstance().all();

        DialogBuilder b = new DialogBuilder("§6Aliases", Page.ALIAS_LIST, null)
                .text(all.isEmpty() ? "§7There are no aliases." : "§7Click an alias to manage it.");

        for (Map.Entry<String, String> entry : all.entrySet()) {
            b.open("/" + entry.getKey() + " → /" + entry.getValue(), Page.ALIAS_ENTRY, entry.getKey());
        }

        return b.back(Page.ALIAS).build();
    }

    static Dialog aliasEntry(String name) {
        String command = name == null ? null : AliasManager.getInstance().all().get(name);

        DialogBuilder b = new DialogBuilder("§6/" + name, Page.ALIAS_ENTRY, name);

        if (command == null) {
            return b.text("§cThis alias does not exist (anymore).")
                    .back(Page.ALIAS_LIST)
                    .build();
        }

        return b.text("§7/" + name + " works like §f/" + command + "§7, with the same arguments and tab completions.")
                .buttonThen("Remove alias", "alias remove " + name, Page.ALIAS_LIST, null)
                .back(Page.ALIAS_LIST)
                .build();
    }
}
