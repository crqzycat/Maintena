package crqzycat.maintena.gui;

import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dialog.Dialog;

/**
 * Einstieg der Settings-GUI (nur für Admins, geöffnet über /maintena): wählt die richtige Seite
 * aus. Die Seiten selbst stehen nach Bereichen getrennt in ServerPages, PunishmentPages und
 * PlayerPages, der gemeinsame Baukasten in DialogBuilder.
 *
 * Aufbau: Übersichtsseiten haben Buttons für Aktionen und für Listen. Eine Liste (z.B. die
 * Whitelist) ist eine eigene Seite mit einem Button pro Eintrag; ein Klick darauf öffnet die
 * Verwaltungsseite des Eintrags (z.B. "Remove from whitelist"). Nach einer Aktion geht es
 * automatisch auf die passende Seite zurück, damit man nicht neu öffnen muss.
 *
 * Alle Buttons senden eine custom click action an den Server (siehe SettingsGUIHandler), die
 * Werte der Eingabefelder werden dort über $(key) in den Befehl eingesetzt.
 */
public final class SettingsGUI {

    private SettingsGUI() {
    }

    // ==================== Einstieg ====================

    public static Dialog build(Page page, CommandSourceStack a, String arg) {
        return switch (page) {
            case MAIN -> main();
            case MAINTENANCE -> ServerPages.maintenance();
            case MAINTENANCE_WHITELIST -> ServerPages.whitelist();
            case MAINTENANCE_PLAYER -> ServerPages.whitelistPlayer(arg);
            case RESTART -> ServerPages.restart();
            case RESTART_SCHEDULES -> ServerPages.restartSchedules();
            case RESTART_SCHEDULE -> ServerPages.restartSchedule(arg);
            case RESTART_ADD -> ServerPages.restartAdd();
            case ANNOUNCE -> ServerPages.announce();
            case ANNOUNCE_SCHEDULES -> ServerPages.announceSchedules();
            case ANNOUNCE_SCHEDULE -> ServerPages.announceSchedule(arg);
            case ANNOUNCE_ADD -> ServerPages.announceAdd();
            case BAN -> PunishmentPages.ban(a);
            case BAN_LIST -> PunishmentPages.banList(a);
            case BAN_ENTRY -> PunishmentPages.banEntry(a, arg);
            case IP_BAN -> PunishmentPages.ipBan(a);
            case IPBAN_LIST -> PunishmentPages.ipBanList(a);
            case IPBAN_ENTRY -> PunishmentPages.ipBanEntry(a, arg);
            case IP_ONLINE -> PunishmentPages.ipOnline(a);
            case IP_PLAYER -> PunishmentPages.ipPlayer(arg);
            case MUTE -> PunishmentPages.mute();
            case MUTE_LIST -> PunishmentPages.muteList();
            case MUTE_ENTRY -> PunishmentPages.muteEntry(arg);
            case VANISH -> PlayerPages.vanish(a);
            case DISGUISE -> PlayerPages.disguise(a);
            case FREEZE -> PunishmentPages.freeze();
            case FREEZE_LIST -> PunishmentPages.freezeList();
            case FREEZE_ENTRY -> PunishmentPages.freezeEntry(a, arg);
            case NICK -> PlayerPages.nick(a);
            case ALIAS -> PlayerPages.alias();
            case ALIAS_LIST -> PlayerPages.aliasList();
            case ALIAS_ENTRY -> PlayerPages.aliasEntry(arg);
        };
    }
    public static Dialog noAccess() {
        return DialogBuilder.noAccess();
    }

    // ==================== Hauptmenü ====================

    private static Dialog main() {
        return new DialogBuilder("§6Maintena Settings", Page.MAIN, null)
                .text("§7Choose an area to configure.")
                .open("Maintenance", Page.MAINTENANCE)
                .open("Restart", Page.RESTART)
                .open("Announcements", Page.ANNOUNCE)
                .open("Bans", Page.BAN)
                .open("IP bans", Page.IP_BAN)
                .open("Mutes", Page.MUTE)
                .open("Vanish", Page.VANISH)
                .open("Disguise", Page.DISGUISE)
                .open("Freeze", Page.FREEZE)
                .open("Nicknames", Page.NICK)
                .open("Aliases", Page.ALIAS)
                .button("Reload config", "maintena reload")
                .build();
    }
}
