package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import crqzycat.maintena.gui.SettingsGUIHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * /settingsgui [seite]  öffnet die Settings-GUI (nur für Spieler).
 *
 * Der Befehl selbst ist für alle erlaubt: Was in der GUI sichtbar ist, entscheidet
 * SettingsGUIHandler anhand der Rechte des Spielers. Die Buttons in den Dialogen rufen
 * ebenfalls /settingsgui <seite> auf, um zwischen den Seiten zu wechseln.
 */
public class SettingsGuiCommandHandler {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        Commands.literal("settingsgui")
                                .executes(ctx -> {
                                    SettingsGUIHandler.open(ctx.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(Commands.argument("page", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SettingsGUIHandler.Page.values()).map(page -> page.id),
                                                builder
                                        ))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "page");
                                            SettingsGUIHandler.Page page = SettingsGUIHandler.Page.byId(id);

                                            if (page == null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("§c✗ Unknown page: " + id)
                                                );
                                                return 0;
                                            }

                                            SettingsGUIHandler.open(ctx.getSource().getPlayerOrException(), page);
                                            return 1;
                                        })
                                )
                )
        );
    }
}