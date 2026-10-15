package crqzycat.maintena.alias;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import crqzycat.maintena.util.CommandTreeUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Command-Aliase.
 *
 * Alias für einen Befehl: /alias tp baum macht /baum zu einem zweiten Namen von /tp.
 * Der Alias ist ein Brigadier-Knoten, der auf den Knoten des Originals weiterleitet (redirect).
 * Dadurch gelten automatisch alle Argumente, Rechte und Tab-Vervollständigungen des Originals.
 *
 * Alias für einen Unterbefehl: /alias sub gamemode creative gmc macht /gmc zu /gamemode creative.
 * Der Unterbefehl kann ein Argument mit festem Wert sein (creative), das sich nicht weiterleiten
 * lässt. Deshalb führt /gmc <rest> den Befehl "gamemode creative <rest>" aus, und die
 * Tab-Vervollständigung für <rest> fragt den echten Befehl.
 *
 * Gespeichert in config/maintena/aliases.json (Alias -> Befehl, bei Unterbefehlen "gamemode creative").
 * Weil der Befehlsbaum bei jedem Serverstart und bei /reload neu gebaut wird, hängt
 * AliasCommandHandler die Aliase danach wieder ein.
 */
public final class AliasManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("maintena").resolve("aliases.json");

    // Muss NACH den anderen statischen Feldern stehen: der Konstruktor lädt die Datei (FILE, GSON, MAP_TYPE)
    private static final AliasManager INSTANCE = new AliasManager();

    /** Alias -> Befehl (ein Wort, oder Befehl + Unterbefehl) */
    private final Map<String, String> aliases = new LinkedHashMap<>();

    /**
     * Aliase, die wirklich im Befehlsbaum stehen. Ein Alias, dessen Name schon ein anderer Befehl
     * belegt hat (Warnung beim Start), ist in aliases, aber hier nicht: sein Knoten gehört nicht uns
     * und darf beim Entfernen oder Neuladen nicht gelöscht werden.
     */
    private final Set<String> registered = new HashSet<>();

    private AliasManager() {
        load();
    }

    public static AliasManager getInstance() {
        return INSTANCE;
    }

    public synchronized Map<String, String> all() {
        return new LinkedHashMap<>(aliases);
    }

    public synchronized boolean isAlias(String name) {
        return aliases.containsKey(name);
    }

    /** Hängt alle gespeicherten Aliase in einen frisch gebauten Befehlsbaum ein. */
    public synchronized void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        registered.clear();

        for (Map.Entry<String, String> entry : all().entrySet()) {
            String error = register(dispatcher, entry.getKey(), entry.getValue(), null);

            if (error != null) {
                LOGGER.warn("[Alias] /{} for /{} was not registered: {}", entry.getKey(), entry.getValue(), error);
            } else {
                registered.add(entry.getKey());
            }
        }
    }

    /**
     * Lädt aliases.json neu (z.B. nach einer Änderung von Hand) und passt den Befehlsbaum sofort an.
     * Ist die Datei nicht lesbar, bleibt alles, wie es war (der Fehler steht im Log).
     */
    public synchronized void reload(MinecraftServer server) {
        Map<String, String> loaded = readFile();

        if (loaded == null) {
            return;
        }

        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

        for (String alias : List.copyOf(registered)) {
            removeNode(dispatcher, alias);
        }

        aliases.clear();
        aliases.putAll(loaded);

        registerAll(dispatcher);
        resendCommands(server);
    }

    /**
     * Legt einen neuen Alias an (sofort aktiv, bleibt nach Neustart erhalten).
     *
     * @param command der Befehl ("tp") oder Befehl + Unterbefehl ("gamemode creative")
     * @return eine Fehlermeldung oder null bei Erfolg
     */
    public synchronized String add(MinecraftServer server, CommandSourceStack source, String command, String alias) {
        command = command.trim().replaceAll("\\s+", " ");

        String rootName = command.split(" ", 2)[0];

        if (alias.equals(command)) {
            return "The alias and the command are the same";
        }

        if (aliases.containsKey(rootName)) {
            return "/" + rootName + " is an alias itself, use the original command /" + aliases.get(rootName);
        }

        if (aliases.containsKey(alias)) {
            return "/" + alias + " is already an alias for /" + aliases.get(alias) + " (remove it first)";
        }

        String error = register(server.getCommands().getDispatcher(), alias, command, source);

        if (error != null) {
            return error;
        }

        aliases.put(alias, command);
        registered.add(alias);
        save();
        resendCommands(server);

        return null;
    }

    /** @return false, wenn es diesen Alias nicht gibt */
    public synchronized boolean remove(MinecraftServer server, String alias) {
        if (aliases.remove(alias) == null) {
            return false;
        }

        if (registered.remove(alias)) {
            removeNode(server.getCommands().getDispatcher(), alias);
        }

        save();
        resendCommands(server);

        return true;
    }

    /**
     * Tab-Vervollständigung des echten Befehls für den Rest, den der Spieler nach dem Alias tippt.
     * Fragt den Befehlsbaum nach "<path> <getippter Rest>" und verschiebt die Treffer zurück.
     */
    public static CompletableFuture<Suggestions> suggestFor(
            CommandSourceStack source, String path, SuggestionsBuilder builder) {

        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
        String full = path + " " + builder.getRemaining();
        int shift = path.length() + 1;

        return dispatcher.getCompletionSuggestions(dispatcher.parse(full, source)).thenApply(result -> {
            if (result.isEmpty()) {
                return result;
            }

            SuggestionsBuilder out = builder.createOffset(
                    builder.getStart() + Math.max(0, result.getRange().getStart() - shift));

            for (Suggestion suggestion : result.getList()) {
                out.suggest(suggestion.getText(), suggestion.getTooltip());
            }

            return out.build();
        });
    }

    // ==================== intern ====================

    /** @param source Absender zum Prüfen eines Unterbefehls (null beim Laden gespeicherter Aliase) */
    private static String register(CommandDispatcher<CommandSourceStack> dispatcher,
                                   String alias, String command, CommandSourceStack source) {

        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        String rootName = command.split(" ", 2)[0];
        CommandNode<CommandSourceStack> named = root.getChild(rootName);

        if (named == null) {
            return "The command /" + rootName + " does not exist";
        }

        if (root.getChild(alias) != null) {
            return "/" + alias + " already exists as a command";
        }

        if (command.contains(" ")) {
            return registerSub(dispatcher, alias, command, named, source);
        }

        // Ist das Original selbst eine Weiterleitung (z.B. /w -> /msg), bis zum echten Ziel folgen
        CommandNode<CommandSourceStack> target = named;

        while (target.getRedirect() != null) {
            target = target.getRedirect();
        }

        // Dieselben Rechte wie das Original (sonst könnte jeder den Alias benutzen)
        Predicate<CommandSourceStack> requirement = named.getRequirement().and(target.getRequirement());

        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(alias).requires(requirement);

        if (target.getCommand() != null) {
            builder.executes(target.getCommand());
        }

        builder.redirect(target);
        dispatcher.register(builder);

        return null;
    }

    /** Alias für "befehl unterbefehl": führt den echten Befehl mit dem getippten Rest aus. */
    private static String registerSub(CommandDispatcher<CommandSourceStack> dispatcher, String alias,
                                      String path, CommandNode<CommandSourceStack> named,
                                      CommandSourceStack source) {

        if (source != null) {
            ParseResults<CommandSourceStack> parse = dispatcher.parse(path, source);

            if (parse.getReader().canRead() || parse.getContext().getNodes().size() < 2) {
                return "/" + path + " is not a valid command";
            }
        }

        // Die Rechte des Original-Befehls; die genauen Rechte des Unterbefehls prüft er beim Ausführen
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(alias)
                .requires(named.getRequirement())
                .executes(ctx -> run(ctx.getSource(), path))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, suggestions) -> suggestFor(ctx.getSource(), path, suggestions))
                        .executes(ctx -> run(ctx.getSource(),
                                path + " " + StringArgumentType.getString(ctx, "args"))));

        dispatcher.register(builder);

        return null;
    }

    private static int run(CommandSourceStack source, String command) {
        source.getServer().getCommands().performPrefixedCommand(source, command);
        return 1;
    }

    /** Entfernt einen Knoten aus dem Befehlsbaum (über CommandTreeUtil). */
    private static void removeNode(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        if (!CommandTreeUtil.removeRootChildren(dispatcher, name)) {
            LOGGER.error("[Alias] Could not remove /{} from the command tree, it is gone after a restart", name);
        }
    }

    /** Schickt allen Spielern den neuen Befehlsbaum, damit die Tab-Vervollständigung sofort stimmt. */
    private static void resendCommands(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private void load() {
        Map<String, String> loaded = readFile();

        if (loaded != null) {
            aliases.putAll(loaded);
        }
    }

    /** @return die gespeicherten Aliase (leer, wenn es die Datei nicht gibt), null bei einem Lesefehler */
    private static Map<String, String> readFile() {
        if (!Files.exists(FILE)) {
            return new LinkedHashMap<>();
        }

        try {
            Map<String, String> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), MAP_TYPE);

            return loaded != null ? loaded : new LinkedHashMap<>();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Alias] Could not read {}", FILE, e);
            return null;
        }
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(aliases, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[Alias] Could not save {}", FILE, e);
        }
    }
}
