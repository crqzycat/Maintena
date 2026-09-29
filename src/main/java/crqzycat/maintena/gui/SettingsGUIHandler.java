package crqzycat.maintena.gui;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

/**
 * Entscheidet, welche Teile der Settings-GUI ein Spieler sehen darf, und öffnet sie.
 *
 * Die Rechte werden nicht doppelt gepflegt: Für jeden Command wird der echte Befehlsknoten
 * aus dem Dispatcher gefragt (CommandNode#canUse). Damit gilt automatisch dieselbe Regel wie
 * beim Tippen des Befehls (Admin/Gamemaster oder ein Permission-Mod, der die Befehle freigibt).
 * Die Befehle prüfen die Rechte beim Ausführen zusätzlich selbst, die GUI ist also nur eine
 * Ansicht und kein Sicherheitsrisiko.
 */
public final class SettingsGUIHandler {

    private SettingsGUIHandler() {
    }

    // ==================== Bereiche ====================

    /** Ein Bereich ist sichtbar, wenn mindestens einer seiner Befehle nutzbar ist. */
    public enum Section {
        MAINTENANCE("maintenance"),
        RESTART("restart"),
        ANNOUNCE("announce"),
        BAN("ban", "unban", "banlist"),
        IP_BAN("ip", "ipban", "ipunban", "ipbanlist");

        private final String[] commands;

        Section(String... commands) {
            this.commands = commands;
        }

        public boolean isVisibleFor(Access access) {
            for (String command : commands) {
                if (access.can(command)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ==================== Seiten ====================

    public enum Page {
        MAIN("main", null),
        MAINTENANCE("maintenance", Section.MAINTENANCE),
        RESTART("restart", Section.RESTART),
        RESTART_ADD("restart_add", Section.RESTART),
        ANNOUNCE("announce", Section.ANNOUNCE),
        ANNOUNCE_ADD("announce_add", Section.ANNOUNCE),
        BAN("ban", Section.BAN),
        IP_BAN("ipban", Section.IP_BAN);

        public final String id;
        public final Section section;

        Page(String id, Section section) {
            this.id = id;
            this.section = section;
        }

        public static Page byId(String id) {
            for (Page page : values()) {
                if (page.id.equalsIgnoreCase(id)) {
                    return page;
                }
            }
            return null;
        }

        public boolean isAllowedFor(Access access) {
            return section == null || section.isVisibleFor(access);
        }
    }

    // ==================== Rechte ====================

    public static final class Access {

        private final CommandSourceStack source;

        public Access(CommandSourceStack source) {
            this.source = source;
        }

        public CommandSourceStack source() {
            return source;
        }

        public MinecraftServer server() {
            return source.getServer();
        }

        /** Darf der Spieler den Befehl (bzw. Unterbefehl-Pfad) ausführen? */
        public boolean can(String... path) {
            CommandNode<CommandSourceStack> node = server().getCommands().getDispatcher().getRoot();

            for (String part : path) {
                node = node.getChild(part);

                if (node == null) {
                    return false;
                }
            }

            return node.canUse(source);
        }

        /** Admin = darf den globalen /maintena Befehl benutzen (Gamemaster oder höher). */
        public boolean isAdmin() {
            return can("maintena");
        }

        public boolean hasAnySection() {
            for (Section section : Section.values()) {
                if (section.isVisibleFor(this)) {
                    return true;
                }
            }
            return isAdmin();
        }
    }

    // ==================== Öffnen ====================

    public static void open(ServerPlayer player) {
        open(player, Page.MAIN);
    }

    public static void open(ServerPlayer player, Page page) {
        Access access = new Access(player.createCommandSourceStack());

        Dialog dialog;

        if (!access.hasAnySection() || !page.isAllowedFor(access)) {
            dialog = SettingsGUI.noAccess();
        } else {
            dialog = SettingsGUI.build(page, access);
        }

        player.openDialog(Holder.direct(dialog));
    }
}