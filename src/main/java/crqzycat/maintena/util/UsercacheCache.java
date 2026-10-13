package crqzycat.maintena.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Hält usercache.json (Name und UUID aller bekannten Spieler) im Speicher.
 *
 * <p>Bei jedem Zugriff wird nur Änderungszeit und Größe der Datei geprüft (günstig). Gelesen
 * wird sie erst wieder, wenn sie sich geändert hat. Das ersetzt das Einlesen der kompletten
 * Datei bei jedem Tastendruck in der Tab-Vervollständigung.
 *
 * <p>Der Zustand ist ein unveränderlicher Snapshot in einem volatile-Feld, also ohne Locks von
 * jedem Thread lesbar.
 */
public final class UsercacheCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");

    private record Snapshot(long modified, long size, Map<String, UUID> entries) {
    }

    private static final Snapshot EMPTY = new Snapshot(-1L, -1L, Map.of());

    private static volatile Snapshot snapshot = EMPTY;

    private UsercacheCache() {
    }

    /**
     * @return Name (Groß-/Kleinschreibung egal) zu UUID, schreibgeschützt. Leer, wenn es die
     *         Datei nicht gibt oder sie nicht gelesen werden konnte.
     */
    public static Map<String, UUID> get() {
        Path file = FabricLoader.getInstance().getGameDir().resolve("usercache.json");

        long modified;
        long size;

        try {
            modified = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (NoSuchFileException e) {
            snapshot = EMPTY;
            return EMPTY.entries();
        } catch (IOException e) {
            // Datei gerade nicht lesbar: letzten bekannten Stand weiterverwenden
            return snapshot.entries();
        }

        Snapshot current = snapshot;

        if (current.modified() == modified && current.size() == size) {
            return current.entries();
        }

        synchronized (UsercacheCache.class) {
            current = snapshot;

            if (current.modified() == modified && current.size() == size) {
                return current.entries();
            }

            Snapshot loaded = new Snapshot(modified, size, read(file, current.entries()));
            snapshot = loaded;
            return loaded.entries();
        }
    }

    private static Map<String, UUID> read(Path file, Map<String, UUID> fallback) {
        Map<String, UUID> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray entries = JsonParser.parseReader(reader).getAsJsonArray();

            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();

                if (!entry.has("name") || !entry.has("uuid")) {
                    continue;
                }

                try {
                    result.put(entry.get("name").getAsString(), UUID.fromString(entry.get("uuid").getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // Eintrag mit ungültiger UUID überspringen
                }
            }
        } catch (Exception e) {
            // Zum Beispiel, wenn der Server die Datei gerade schreibt: alten Stand behalten
            LOGGER.warn("[Usercache] Could not read usercache.json, keeping the previous list", e);
            return fallback;
        }

        return Collections.unmodifiableMap(result);
    }
}
