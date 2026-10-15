package crqzycat.maintena.gui;

import crqzycat.maintena.ban.BanManager;
import crqzycat.maintena.ban.IpBanManager;
import crqzycat.maintena.freeze.FreezeManager;
import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import crqzycat.maintena.mute.MuteData;
import crqzycat.maintena.mute.MuteManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.UserBanListEntry;
import java.util.Collection;
import java.util.List;

/**
 * Dialog-Seiten für Bans, IP-Bans, Mutes und Freeze.
 */
final class PunishmentPages {

    private PunishmentPages() {
    }

    // ==================== Bans ====================

    static Dialog ban(CommandSourceStack a) {
        int count = BanManager.getInstance().getActiveBans(a.getServer()).size();

        return new DialogBuilder("§6Bans", Page.BAN, null)
                .text("§7Duration examples: 30m, 24h, 7d, 1d12h")
                .textInput("player", "Player name", "", 16)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Ban permanently", "ban $(player) $(reason)")
                .button("Ban temporarily", "ban $(player) $(duration) $(reason)")
                .open("Banned players (" + count + ")", Page.BAN_LIST)
                .button("Show ban list", "banlist")
                .back(Page.MAIN)
                .build();
    }

    static Dialog banList(CommandSourceStack a) {
        List<String> banned = BanManager.getInstance().getActiveBans(a.getServer())
                .stream()
                .map(entry -> entry.getUser().name())
                .toList();

        DialogBuilder b = new DialogBuilder("§6Banned players", Page.BAN_LIST, null)
                .text(banned.isEmpty() ? "§7No players are banned." : "§7Click a player to manage them.");

        for (String name : banned) {
            b.open(name, Page.BAN_ENTRY, name);
        }

        return b.back(Page.BAN).build();
    }

    static Dialog banEntry(CommandSourceStack a, String name) {
        BanManager manager = BanManager.getInstance();
        UserBanListEntry entry = manager.findBan(a.getServer(), name);

        DialogBuilder b = new DialogBuilder("§6" + name, Page.BAN_ENTRY, name);

        if (entry == null) {
            return b.text("§cThis player is not banned (anymore).")
                    .back(Page.BAN_LIST)
                    .build();
        }

        b.text("§7" + manager.describeRemaining(entry));

        if (entry.getReason() != null && !entry.getReason().isBlank()) {
            b.text("§7Reason: §f" + entry.getReason());
        }

        return b.buttonThen("Unban", "unban " + name, Page.BAN_LIST, null)
                .button("Ban info (chat)", "ban info " + name)
                .back(Page.BAN_LIST)
                .build();
    }

    // ==================== IP bans ====================

    static Dialog ipBan(CommandSourceStack a) {
        int count = IpBanManager.getInstance().getActiveBans(a.getServer()).size();

        return new DialogBuilder("§6IP bans", Page.IP_BAN, null)
                .text("§7Target: player name or IP address. Duration examples: 30m, 24h, 7d")
                .textInput("target", "Player or IP", "", 45)
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("IP ban permanently", "ipban $(target) $(reason)")
                .button("IP ban temporarily", "ipban $(target) $(duration) $(reason)")
                .open("Banned IPs (" + count + ")", Page.IPBAN_LIST)
                .open("Online players", Page.IP_ONLINE)
                .button("Show IP ban list", "ipbanlist")
                .back(Page.MAIN)
                .build();
    }

    static Dialog ipBanList(CommandSourceStack a) {
        List<String> ips = IpBanManager.getInstance().getActiveBans(a.getServer())
                .stream()
                .map(IpBanListEntry::getUser)
                .toList();

        DialogBuilder b = new DialogBuilder("§6Banned IPs", Page.IPBAN_LIST, null)
                .text(ips.isEmpty() ? "§7No IPs are banned." : "§7Click an IP to manage it.");

        for (String ip : ips) {
            b.open(ip, Page.IPBAN_ENTRY, ip);
        }

        return b.back(Page.IP_BAN).build();
    }

    static Dialog ipBanEntry(CommandSourceStack a, String ip) {
        IpBanManager manager = IpBanManager.getInstance();
        IpBanListEntry entry = manager.findBan(a.getServer(), ip);

        DialogBuilder b = new DialogBuilder("§6" + ip, Page.IPBAN_ENTRY, ip);

        if (entry == null) {
            return b.text("§cThis IP is not banned (anymore).")
                    .back(Page.IPBAN_LIST)
                    .build();
        }

        b.text("§7" + manager.describeRemaining(entry));

        if (entry.getReason() != null && !entry.getReason().isBlank()) {
            b.text("§7Reason: §f" + entry.getReason());
        }

        return b.buttonThen("Remove IP ban", "ipunban " + quoteIp(ip), Page.IPBAN_LIST, null)
                .button("IP ban info (chat)", "ipban info " + quoteIp(ip))
                .back(Page.IPBAN_LIST)
                .build();
    }

    /** IPv6 enthält ":" und muss im Befehl in Anführungszeichen stehen. */
    private static String quoteIp(String ip) {
        return ip.contains(":") ? "\"" + ip + "\"" : ip;
    }

    static Dialog ipOnline(CommandSourceStack a) {
        Collection<String> online = a.getOnlinePlayerNames();

        DialogBuilder b = new DialogBuilder("§6Online players", Page.IP_ONLINE, null)
                .text(online.isEmpty() ? "§7Nobody is online." : "§7Click a player to manage their IP.");

        for (String name : online) {
            b.open(name, Page.IP_PLAYER, name);
        }

        return b.back(Page.IP_BAN).build();
    }

    static Dialog ipPlayer(String name) {
        return new DialogBuilder("§6" + name, Page.IP_PLAYER, name)
                .text("§7Duration examples: 30m, 24h, 7d")
                .textInput("duration", "Duration (temporary ban)", "1d", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Show IP (chat)", "ip " + name)
                .button("IP ban permanently", "ipban " + name + " $(reason)")
                .button("IP ban temporarily", "ipban " + name + " $(duration) $(reason)")
                .back(Page.IP_ONLINE)
                .build();
    }

    // ==================== Mutes (Chat-Timeouts) ====================

    static Dialog mute() {
        int count = MuteManager.getInstance().getActiveMutes().size();

        return new DialogBuilder("§6Mutes", Page.MUTE, null)
                .text("§7Muted players can't chat, but can still use commands. Duration examples: 30m, 24h, 7d, 1d12h")
                .textInput("player", "Player name", "", 16)
                .textInput("duration", "Duration (temporary mute)", "30m", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Mute permanently", "mute $(player) $(reason)")
                .button("Mute temporarily", "mute $(player) $(duration) $(reason)")
                .open("Muted players (" + count + ")", Page.MUTE_LIST)
                .button("Show mute list", "mutelist")
                .back(Page.MAIN)
                .build();
    }

    static Dialog muteList() {
        List<String> muted = MuteManager.getInstance().getActiveMutes()
                .stream()
                .map(entry -> entry.name)
                .toList();

        DialogBuilder b = new DialogBuilder("§6Muted players", Page.MUTE_LIST, null)
                .text(muted.isEmpty() ? "§7No players are muted." : "§7Click a player to manage them.");

        for (String name : muted) {
            b.open(name, Page.MUTE_ENTRY, name);
        }

        return b.back(Page.MUTE).build();
    }

    static Dialog muteEntry(String name) {
        MuteManager manager = MuteManager.getInstance();
        MuteData.Entry entry = manager.findByName(name);

        DialogBuilder b = new DialogBuilder("§6" + name, Page.MUTE_ENTRY, name);

        if (entry == null) {
            return b.text("§cThis player is not muted (anymore).")
                    .back(Page.MUTE_LIST)
                    .build();
        }

        b.text("§7" + manager.describeRemaining(entry));

        if (entry.reason != null && !entry.reason.isBlank()) {
            b.text("§7Reason: §f" + entry.reason);
        }

        return b.buttonThen("Unmute", "unmute " + name, Page.MUTE_LIST, null)
                .button("Mute info (chat)", "mute info " + name)
                .back(Page.MUTE_LIST)
                .build();
    }

    // ==================== Freeze ====================

    static Dialog freeze() {
        int count = FreezeManager.getInstance().getFrozen().size();

        return new DialogBuilder("§6Freeze", Page.FREEZE, null)
                .text("§7Frozen players can't move or interact with the world (blocks, items, entities). "
                        + "They get instructions on their screen and keep the freeze when they re-join. Currently frozen: " + count)
                .textInput("player", "Player name (must be online)", "", 16)
                .textInput("reason", "Reason (optional)", "", 128)
                .button("Freeze", "freeze $(player) $(reason)")
                .button("Unfreeze", "unfreeze $(player)")
                .open("Frozen players (" + count + ")", Page.FREEZE_LIST)
                .button("Show frozen players (chat)", "freeze list")
                .back(Page.MAIN)
                .build();
    }

    static Dialog freezeList() {
        List<String> frozen = FreezeManager.getInstance().getFrozen()
                .stream()
                .map(entry -> entry.name)
                .toList();

        DialogBuilder b = new DialogBuilder("§6Frozen players", Page.FREEZE_LIST, null)
                .text(frozen.isEmpty() ? "§7Nobody is frozen." : "§7Click a player to manage them.");

        for (String name : frozen) {
            b.open(name, Page.FREEZE_ENTRY, name);
        }

        if (!frozen.isEmpty()) {
            b.buttonThen("Unfreeze everyone", "unfreeze all", Page.FREEZE_LIST, null);
        }

        return b.back(Page.FREEZE).build();
    }

    static Dialog freezeEntry(CommandSourceStack a, String name) {
        FreezeManager manager = FreezeManager.getInstance();
        FreezeManager.Entry entry = manager.findByName(name);

        DialogBuilder b = new DialogBuilder("§6" + name, Page.FREEZE_ENTRY, name);

        if (entry == null) {
            return b.text("§cThis player is not frozen (anymore).")
                    .back(Page.FREEZE_LIST)
                    .build();
        }

        boolean online = a.getServer().getPlayerList().getPlayer(entry.id) != null;

        b.text("§7Frozen by §f" + entry.source + " §7on §f" + manager.describeSince(entry)
                + (online ? "" : " §c(offline)"));

        if (entry.reason != null && !entry.reason.isBlank()) {
            b.text("§7Reason: §f" + entry.reason);
        }

        return b.buttonThen("Unfreeze", "unfreeze " + name, Page.FREEZE_LIST, null)
                .back(Page.FREEZE_LIST)
                .build();
    }
}
