package crqzycat.maintena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import crqzycat.maintena.alias.AliasManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Alias-Befehle (Gamemaster/OP):
 *   /alias <command> <alias>   /<alias> funktioniert danach wie /<command>, mit denselben Argumenten
 *                              und Tab-Vervollständigungen (z.B. /alias tp baum)
 *   /alias sub <command> <sub-command> <alias>
 *                              /<alias> wird zu "/<command> <sub-command>", z.B. /alias sub gamemode creative gmc
 *                              (mehrere Wörter in Anführungszeichen: /alias sub time "set day" day)
 *   /alias remove <alias>      Alias entfernen
 *   /alias list                alle Aliase
 *
 * Aliase bleiben nach einem Neustart erhalten (config/maintena/aliases.json).
 */
public final class AliasCommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1,32}");

    private AliasCommandHandler() {
    }

    /**
     * Hängt die gespeicherten Aliase ein, NACHDEM alle anderen Befehle (auch die anderer Mods)
     * registriert sind. Läuft bei jedem Aufbau des Befehlsbaums, also auch bei /reload.
     */
    public static void register() {
        Identifier late = Identifier.fromNamespaceAndPath("maintena", "aliases");

        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        CommandRegistrationCallback.EVENT.register(late, (dispatcher, registryAccess, environment) ->
                AliasManager.getInstance().registerAll(dispatcher));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildAliasTree() {
        return Commands.literal("alias")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("§7Usage: §f/alias <command> <alias>§7, "
                                    + "§f/alias sub <command> <sub-command> <alias>§7, "
                                    + "§f/alias remove <alias>§7, §f/alias list"),
                            false
                    );
                    return 1;
                })

                // /alias sub gamemode creative gmc  ->  /gmc = /gamemode creative
                .then(Commands.literal("sub")
                        .then(Commands.argument("command", StringArgumentType.word())
                                .suggests(AliasCommandHandler::suggestCommands)
                                .then(Commands.argument("sub", StringArgumentType.string())
                                        .suggests((ctx, builder) -> AliasManager.suggestFor(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "command"), builder))
                                        .then(Commands.argument("alias", StringArgumentType.word())
                                                .executes(ctx -> add(ctx,
                                                        StringArgumentType.getString(ctx, "command") + " "
                                                                + StringArgumentType.getString(ctx, "sub")))))))

                // "list" ist auch ein Vanilla-Befehl: /alias list <alias> legt einen Alias dafür an
                .then(Commands.literal("list")
                        .executes(AliasCommandHandler::list)
                        .then(Commands.argument("alias", StringArgumentType.word())
                                .executes(ctx -> add(ctx, "list"))))

                .then(Commands.literal("remove")
                        .then(Commands.argument("alias", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        AliasManager.getInstance().all().keySet(), builder))
                                .executes(AliasCommandHandler::remove)))

                .then(Commands.argument("command", StringArgumentType.word())
                        .suggests(AliasCommandHandler::suggestCommands)
                        .then(Commands.argument("alias", StringArgumentType.word())
                                .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "command")))));
    }

    /** Alle Befehle, die der Spieler benutzen darf (ohne bestehende Aliase). */
    private static CompletableFuture<Suggestions> suggestCommands(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {

        CommandSourceStack source = ctx.getSource();
        AliasManager manager = AliasManager.getInstance();

        return SharedSuggestionProvider.suggest(
                source.getServer().getCommands().getDispatcher().getRoot().getChildren().stream()
                        .filter(node -> node instanceof LiteralCommandNode && node.canUse(source))
                        .map(CommandNode::getName)
                        .filter(name -> !manager.isAlias(name) && name.matches("[A-Za-z0-9_-]+")),
                builder);
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String command) {
        CommandSourceStack source = ctx.getSource();
        String alias = StringArgumentType.getString(ctx, "alias").toLowerCase(Locale.ROOT);

        if (!VALID.matcher(alias).matches()) {
            source.sendFailure(Component.literal("§c✗ An alias needs 1-32 characters: letters, digits, - and _"));
            return 0;
        }

        String error = AliasManager.getInstance().add(source.getServer(), source, command, alias);

        if (error != null) {
            source.sendFailure(Component.literal("§c✗ " + error));
            return 0;
        }

        LOGGER.info("[Alias] {} created /{} for /{}", source.getTextName(), alias, command);
        source.sendSuccess(() -> Component.literal("§a✓ §f/" + alias + " §7now works like §f/" + command), false);

        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String alias = StringArgumentType.getString(ctx, "alias").toLowerCase(Locale.ROOT);

        if (!AliasManager.getInstance().remove(source.getServer(), alias)) {
            source.sendFailure(Component.literal("§c✗ There is no alias /" + alias));
            return 0;
        }

        LOGGER.info("[Alias] {} removed /{}", source.getTextName(), alias);
        source.sendSuccess(() -> Component.literal("§a✓ Removed the alias §f/" + alias), false);

        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Map<String, String> all = AliasManager.getInstance().all();

        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7There are no aliases"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§bAliases §7(" + all.size() + ")§b:"), false);

        for (Map.Entry<String, String> entry : all.entrySet()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§7- §f/" + entry.getKey() + " §7→ §f/" + entry.getValue()), false);
        }

        return all.size();
    }
}
