package crqzycat.maintena.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import crqzycat.maintena.announcement.AnnouncementData;
import crqzycat.maintena.ban.BanConfig;
import crqzycat.maintena.freeze.FreezeConfig;
import crqzycat.maintena.maintenance.MaintenanceData;
import crqzycat.maintena.mute.MuteConfig;
import crqzycat.maintena.mute.MuteData;
import crqzycat.maintena.restart.RestartData;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Lädt und speichert alle JSON-Dateien von Maintena.
 *
 * <ul>
 *   <li>Immer UTF-8 (unabhängig vom System-Charset).</li>
 *   <li>Schreiben ist atomar: erst in eine .tmp-Datei, dann umbenennen. Ein Absturz mitten im
 *       Schreiben lässt die alte Datei unverändert.</li>
 *   <li>Kaputte oder unlesbare Dateien crashen den Server nicht: sie werden als
 *       .corrupt-&lt;zeit&gt; zur Seite gelegt und es werden Standardwerte geladen.</li>
 * </ul>
 */
public class PersistenceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("Maintena");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("maintena");
    private static final File DATA_FILE = CONFIG_DIR.resolve("maintenance.json").toFile();
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("config.json").toFile();
    private static final File RESTART_DATA_FILE = CONFIG_DIR.resolve("restarts.json").toFile();
    private static final File RESTART_CONFIG_FILE = CONFIG_DIR.resolve("restart-config.json").toFile();
    private static final File ANNOUNCEMENT_DATA_FILE = CONFIG_DIR.resolve("announcements.json").toFile();
    private static final File ANNOUNCEMENT_CONFIG_FILE = CONFIG_DIR.resolve("announcement-config.json").toFile();
    private static final File BAN_CONFIG_FILE = CONFIG_DIR.resolve("ban-config.json").toFile();
    private static final File MUTE_DATA_FILE = CONFIG_DIR.resolve("mutes.json").toFile();
    private static final File MUTE_CONFIG_FILE = CONFIG_DIR.resolve("mute-config.json").toFile();
    private static final File FREEZE_CONFIG_FILE = CONFIG_DIR.resolve("freeze-config.json").toFile();

    static {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("[Persistence] Could not create {}", CONFIG_DIR, e);
        }
    }

    // ==================== Low-Level ====================

    /** Schreibt atomar und in UTF-8. Gibt false zurück, wenn es nicht geklappt hat. */
    private static synchronized boolean writeJson(File file, Object value) {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Persistence] Could not save {}", file.getName(), e);

            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Aufräumen ist nur ein Versuch
            }

            return false;
        }
    }

    /**
     * Liest eine JSON-Datei. Gibt null zurück, wenn sie fehlt, leer, unlesbar oder kaputt ist.
     * Kaputte Dateien werden als .corrupt-&lt;zeit&gt; gesichert, damit sie nicht überschrieben werden.
     */
    private static synchronized <T> T readJson(File file, Class<T> type) {
        if (!file.exists()) {
            return null;
        }

        String content;

        try {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[Persistence] Could not read {}", file.getName(), e);
            return null;
        }

        if (content.isBlank()) {
            return null;
        }

        try {
            return GSON.fromJson(content, type);
        } catch (RuntimeException e) {
            quarantine(file, e);
            return null;
        }
    }

    private static void quarantine(File file, Exception cause) {
        Path source = file.toPath();
        Path backup = source.resolveSibling(file.getName() + ".corrupt-" + System.currentTimeMillis());

        try {
            Files.move(source, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.error("[Persistence] {} is not valid JSON, moved to {} and loaded defaults",
                    file.getName(), backup.getFileName(), cause);
        } catch (IOException moveError) {
            LOGGER.error("[Persistence] {} is not valid JSON and could not be moved away, loaded defaults",
                    file.getName(), cause);
        }
    }

    /** Lädt ein Objekt oder die Standardwerte; legt bei fehlender Datei die Standard-Datei an. */
    private static <T> T loadOrDefault(File file, Class<T> type, T defaults, boolean createIfMissing) {
        boolean existed = file.exists();
        T loaded = readJson(file, type);

        if (loaded != null) {
            return loaded;
        }

        if (!existed && createIfMissing) {
            writeJson(file, defaults);
        }

        return defaults;
    }

    private static boolean has(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    private static String string(JsonObject json, String key, String fallback) {
        if (!has(json, key)) {
            return fallback;
        }

        try {
            return json.get(key).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ==================== Maintenance ====================

    public static void saveData(MaintenanceData data) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", data.enabled);
        json.addProperty("endTime", data.endTime);

        JsonArray players = new JsonArray();
        for (MaintenanceData.WhitelistEntry entry : data.whitelist) {
            JsonObject player = new JsonObject();
            player.addProperty("name", entry.name);

            if (entry.uuid != null) {
                player.addProperty("uuid", entry.uuid);
            }

            players.add(player);
        }
        json.add("whitelistedPlayers", players);

        writeJson(DATA_FILE, json);
    }

    public static MaintenanceData loadData() {
        MaintenanceData data = new MaintenanceData();
        JsonObject json = readJson(DATA_FILE, JsonObject.class);

        if (json == null) {
            return data;
        }

        try {
            if (has(json, "enabled")) {
                data.enabled = json.get("enabled").getAsBoolean();
            }

            if (has(json, "endTime")) {
                data.endTime = json.get("endTime").getAsLong();
            }

            if (has(json, "whitelistedPlayers") && json.get("whitelistedPlayers").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("whitelistedPlayers")) {
                    MaintenanceData.WhitelistEntry entry = readWhitelistEntry(element);

                    if (entry != null) {
                        data.whitelist.add(entry);
                    }
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("[Persistence] {} has unexpected values, using what could be read",
                    DATA_FILE.getName(), e);
        }

        return data;
    }

    /**
     * Neues Format: {"name": "...", "uuid": "..."}. Altes Format (nur ein Name als Text)
     * wird weiterhin gelesen und später an die UUID gebunden.
     */
    private static MaintenanceData.WhitelistEntry readWhitelistEntry(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonPrimitive()) {
            String name = element.getAsString();
            return name.isBlank() ? null : new MaintenanceData.WhitelistEntry(name, null);
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        String name = string(object, "name", null);

        if (name == null || name.isBlank()) {
            return null;
        }

        String uuid = string(object, "uuid", null);

        if (uuid != null) {
            try {
                uuid = UUID.fromString(uuid).toString();
            } catch (IllegalArgumentException e) {
                uuid = null;
            }
        }

        return new MaintenanceData.WhitelistEntry(name, uuid);
    }

    public static void saveConfig(MaintenanceData.Config config) {
        JsonObject json = new JsonObject();
        json.addProperty("broadcastStart", config.broadcastStart);
        json.addProperty("broadcastEnd", config.broadcastEnd);
        json.addProperty("broadcastWarning", config.broadcastWarning);
        json.addProperty("statusMessage", config.statusMessage);
        json.addProperty("motdMaintenanceLine", config.motdMaintenanceLine);
        json.addProperty("motdMaintenanceTimed", config.motdMaintenanceTimed);
        json.addProperty("motdMaintenanceNoTime", config.motdMaintenanceNoTime);

        writeJson(CONFIG_FILE, json);
    }

    public static MaintenanceData.Config loadConfig() {
        MaintenanceData.Config config = new MaintenanceData.Config();

        if (!CONFIG_FILE.exists()) {
            saveConfig(config); // Create default config
            return config;
        }

        JsonObject json = readJson(CONFIG_FILE, JsonObject.class);

        if (json != null) {
            config.broadcastStart = string(json, "broadcastStart", config.broadcastStart);
            config.broadcastEnd = string(json, "broadcastEnd", config.broadcastEnd);
            config.broadcastWarning = string(json, "broadcastWarning", config.broadcastWarning);
            config.statusMessage = string(json, "statusMessage", config.statusMessage);
            config.motdMaintenanceLine = string(json, "motdMaintenanceLine", config.motdMaintenanceLine);
            config.motdMaintenanceTimed = string(json, "motdMaintenanceTimed", config.motdMaintenanceTimed);
            config.motdMaintenanceNoTime = string(json, "motdMaintenanceNoTime", config.motdMaintenanceNoTime);
        }

        return config;
    }

    // ==================== Restart Schedules ====================

    public static void saveRestartData(RestartData data) {
        writeJson(RESTART_DATA_FILE, data);
    }

    public static RestartData loadRestartData() {
        RestartData data = loadOrDefault(RESTART_DATA_FILE, RestartData.class, new RestartData(), false);

        if (data.schedules == null) {
            data.schedules = new ArrayList<>();
        }

        data.schedules.removeIf(schedule -> schedule == null || schedule.id == null || schedule.type == null);

        return data;
    }

    public static void saveRestartConfig(RestartData.Config config) {
        writeJson(RESTART_CONFIG_FILE, config);
    }

    public static RestartData.Config loadRestartConfig() {
        RestartData.Config config = loadOrDefault(
                RESTART_CONFIG_FILE, RestartData.Config.class, new RestartData.Config(), true);

        if (config.warningMinutes == null) {
            config.warningMinutes = new RestartData.Config().warningMinutes;
        }

        return config;
    }

    // ==================== Announcement Schedules ====================

    public static void saveAnnouncementData(AnnouncementData data) {
        writeJson(ANNOUNCEMENT_DATA_FILE, data);
    }

    public static AnnouncementData loadAnnouncementData() {
        AnnouncementData data = loadOrDefault(
                ANNOUNCEMENT_DATA_FILE, AnnouncementData.class, new AnnouncementData(), false);

        if (data.schedules == null) {
            data.schedules = new ArrayList<>();
        }

        data.schedules.removeIf(schedule -> schedule == null || schedule.type == null);

        return data;
    }

    public static void saveAnnouncementConfig(AnnouncementData.Config config) {
        writeJson(ANNOUNCEMENT_CONFIG_FILE, config);
    }

    public static AnnouncementData.Config loadAnnouncementConfig() {
        return loadOrDefault(
                ANNOUNCEMENT_CONFIG_FILE, AnnouncementData.Config.class, new AnnouncementData.Config(), true);
    }

    // ==================== Ban Config ====================

    public static void saveBanConfig(BanConfig config) {
        writeJson(BAN_CONFIG_FILE, config);
    }

    public static BanConfig loadBanConfig() {
        return loadOrDefault(BAN_CONFIG_FILE, BanConfig.class, new BanConfig(), true);
    }

    // ==================== Mutes ====================

    public static void saveMuteData(MuteData data) {
        writeJson(MUTE_DATA_FILE, data);
    }

    public static MuteData loadMuteData() {
        MuteData data = loadOrDefault(MUTE_DATA_FILE, MuteData.class, new MuteData(), false);

        if (data.mutes == null) {
            data.mutes = new ArrayList<>();
        }

        data.mutes.removeIf(entry -> entry == null || entry.uuid == null);

        return data;
    }

    public static void saveMuteConfig(MuteConfig config) {
        writeJson(MUTE_CONFIG_FILE, config);
    }

    public static MuteConfig loadMuteConfig() {
        return loadOrDefault(MUTE_CONFIG_FILE, MuteConfig.class, new MuteConfig(), true);
    }

    // ==================== Freeze ====================

    public static void saveFreezeConfig(FreezeConfig config) {
        writeJson(FREEZE_CONFIG_FILE, config);
    }

    public static FreezeConfig loadFreezeConfig() {
        FreezeConfig config = loadOrDefault(FREEZE_CONFIG_FILE, FreezeConfig.class, new FreezeConfig(), true);

        if (config.instructions == null) {
            config.instructions = new FreezeConfig().instructions;
        }

        return config;
    }
}
