package crqzycat.maintena.gui;

import crqzycat.maintena.gui.SettingsGUIHandler.Page;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Baukasten für alle Dialog-Seiten der Settings-GUI: Text, Eingabefelder und Buttons.
 * Die Buttons senden eine custom click action an den Server (siehe SettingsGUIHandler).
 */
final class DialogBuilder {

    private static final int BUTTON_WIDTH = 150;
    private static final int INPUT_WIDTH = 200;
    private static final int TEXT_WIDTH = 300;

    static Dialog noAccess() {
        return notice("§cMaintena Settings", "You don't have permission to use any Maintena commands.");
    }

    static Dialog notice(String title, String message) {
        CommonDialogData common = new CommonDialogData(
                Component.literal(title),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(message), PlainMessage.DEFAULT_WIDTH)),
                List.of()
        );

        return new NoticeDialog(common, NoticeDialog.DEFAULT_ACTION);
    }

    /**
     * Ein Button sendet eine custom click action an den Server (statt einen Befehl auf dem Client
     * auszuführen, was Minecraft mit einer Bestätigungs-Warnung quittieren würde).
     *
     * @param command Befehl mit $(key)-Platzhaltern, oder null (reine Navigation)
     * @param page    Seite, die danach geöffnet wird
     * @param arg     Parameter dieser Seite (z.B. Spielername), oder null
     */
    static ActionButton actionButton(String label, String command, Page page, String arg) {
        CompoundTag additions = new CompoundTag();

        if (command != null) {
            additions.putString(SettingsGUIHandler.KEY_COMMAND, command);
        }

        additions.putString(SettingsGUIHandler.KEY_PAGE, page.name());

        if (arg != null) {
            additions.putString(SettingsGUIHandler.KEY_ARG, arg);
        }

        Action action = new CustomAll(
                Identifier.fromNamespaceAndPath("maintena", SettingsGUIHandler.ACTION_PATH),
                Optional.of(additions)
        );

        return new ActionButton(
                new CommonButtonData(Component.literal(label), BUTTON_WIDTH),
                Optional.of(action)
        );
    }

    // ==================== Baukasten ====================

    private final Component title;
    private final Page page;
    private final String arg;
    private final List<DialogBody> body = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    private final List<ActionButton> buttons = new ArrayList<>();
    private ActionButton exit;

    DialogBuilder(String title, Page page, String arg) {
        this.title = Component.literal(title);
        this.page = page;
        this.arg = arg;
    }

    DialogBuilder text(String message) {
        if (message != null && !message.isBlank()) {
            body.add(new PlainMessage(Component.literal(message), TEXT_WIDTH));
        }
        return this;
    }

    DialogBuilder textInput(String key, String label, String initial, int maxLength) {
        inputs.add(new Input(key, new TextInput(
                INPUT_WIDTH, Component.literal(label), true, initial, maxLength, Optional.empty()
        )));
        return this;
    }

    /** Auswahlfeld für Formulare (z.B. Wochentag). Listen von Einträgen sind stattdessen Buttons. */
    DialogBuilder dropdown(String key, String label, Collection<String> values) {
        if (values.isEmpty()) {
            return this; // leere Auswahl wird vom Client nicht akzeptiert
        }

        List<SingleOptionInput.Entry> entries = new ArrayList<>();
        boolean first = true;

        for (String value : values) {
            entries.add(new SingleOptionInput.Entry(value, Optional.empty(), first));
            first = false;
        }

        inputs.add(new Input(key, new SingleOptionInput(
                INPUT_WIDTH, entries, Component.literal(label), true
        )));
        return this;
    }

    /** Führt den Befehl aus und bleibt danach auf dieser Seite (Anzeige wird aktualisiert). */
    DialogBuilder button(String label, String command) {
        buttons.add(actionButton(label, command, page, arg));
        return this;
    }

    DialogBuilder buttonIf(boolean condition, String label, String command) {
        return condition ? button(label, command) : this;
    }

    /** Führt den Befehl aus und öffnet danach eine andere Seite. */
    DialogBuilder buttonThen(String label, String command, Page next, String nextArg) {
        buttons.add(actionButton(label, command, next, nextArg));
        return this;
    }

    /** Öffnet nur eine andere Seite. */
    DialogBuilder open(String label, Page next) {
        return open(label, next, null);
    }

    DialogBuilder open(String label, Page next, String nextArg) {
        buttons.add(actionButton(label, null, next, nextArg));
        return this;
    }

    DialogBuilder back(Page previous) {
        return back(previous, null);
    }

    DialogBuilder back(Page previous, String previousArg) {
        exit = actionButton("« Back", null, previous, previousArg);
        return this;
    }

    Dialog build() {
        // Ein Dialog braucht mindestens einen Button: gibt es nur "Back", wird er normaler Button
        if (buttons.isEmpty() && exit != null) {
            buttons.add(exit);
            exit = null;
        }

        if (buttons.isEmpty()) {
            return noAccess();
        }

        CommonDialogData common = new CommonDialogData(
                title,
                Optional.empty(),
                true,
                false,
                DialogAction.NONE,
                List.copyOf(body),
                List.copyOf(inputs)
        );

        return new MultiActionDialog(common, List.copyOf(buttons), Optional.ofNullable(exit), 2);
    }

}
