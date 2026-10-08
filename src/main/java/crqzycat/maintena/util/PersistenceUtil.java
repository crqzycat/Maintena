package crqzycat.maintena.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import crqzycat.maintena.announcement.AnnouncementData;
import crqzycat.maintena.ban.BanConfig;
import crqzycat.maintena.maintenance.MaintenanceData;
import crqzycat.maintena.freeze.FreezeConfig;
import crqzycat.maintena.mute.MuteConfig;
import crqzycat.maintena.mute.MuteData;
import crqzycat.maintena.restart.RestartData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistenceUtil {
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
            e.printStackTrace();
        }
    }
    
    public static void saveData(MaintenanceData data) {
        try {
            if (!DATA_FILE.exists()) {
                DATA_FILE.createNewFile();
            }
            
            JsonObject json = new JsonObject();
            json.addProperty("enabled", data.enabled);
            json.addProperty("endTime", data.endTime);
            
            JsonArray players = new JsonArray();
            for (String player : data.whitelistedPlayers) {
                players.add(player);
            }
            json.add("whitelistedPlayers", players);
            
            try (FileWriter writer = new FileWriter(DATA_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static MaintenanceData loadData() {
        MaintenanceData data = new MaintenanceData();
        
        if (!DATA_FILE.exists()) {
            return data;
        }
        
        try (FileReader reader = new FileReader(DATA_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                data.enabled = json.get("enabled").getAsBoolean();
                data.endTime = json.get("endTime").getAsLong();
                
                JsonArray players = json.getAsJsonArray("whitelistedPlayers");
                if (players != null) {
                    for (int i = 0; i < players.size(); i++) {
                        data.whitelistedPlayers.add(players.get(i).getAsString());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return data;
    }
    
    public static void saveConfig(MaintenanceData.Config config) {
        try {
            if (!CONFIG_FILE.exists()) {
                CONFIG_FILE.createNewFile();
            }
            
            JsonObject json = new JsonObject();
            json.addProperty("broadcastStart", config.broadcastStart);
            json.addProperty("broadcastEnd", config.broadcastEnd);
            json.addProperty("broadcastWarning", config.broadcastWarning);
            json.addProperty("statusMessage", config.statusMessage);
            json.addProperty("motdMaintenanceLine", config.motdMaintenanceLine);
            json.addProperty("motdMaintenanceTimed", config.motdMaintenanceTimed);
            json.addProperty("motdMaintenanceNoTime", config.motdMaintenanceNoTime);
            
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static MaintenanceData.Config loadConfig() {
        MaintenanceData.Config config = new MaintenanceData.Config();
        
        if (!CONFIG_FILE.exists()) {
            saveConfig(config); // Create default config
            return config;
        }
        
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("broadcastStart")) config.broadcastStart = json.get("broadcastStart").getAsString();
                if (json.has("broadcastEnd")) config.broadcastEnd = json.get("broadcastEnd").getAsString();
                if (json.has("broadcastWarning")) config.broadcastWarning = json.get("broadcastWarning").getAsString();
                if (json.has("statusMessage")) config.statusMessage = json.get("statusMessage").getAsString();
                if (json.has("motdMaintenanceLine")) config.motdMaintenanceLine = json.get("motdMaintenanceLine").getAsString();
                if (json.has("motdMaintenanceTimed")) config.motdMaintenanceTimed = json.get("motdMaintenanceTimed").getAsString();
                if (json.has("motdMaintenanceNoTime")) config.motdMaintenanceNoTime = json.get("motdMaintenanceNoTime").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return config;
    }

    // ==================== Restart Schedules ====================

    public static void saveRestartData(RestartData data) {
        try {
            if (!RESTART_DATA_FILE.exists()) {
                RESTART_DATA_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(RESTART_DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static RestartData loadRestartData() {
        if (!RESTART_DATA_FILE.exists()) {
            return new RestartData();
        }

        try (FileReader reader = new FileReader(RESTART_DATA_FILE)) {
            RestartData data = GSON.fromJson(reader, RestartData.class);
            return data != null ? data : new RestartData();
        } catch (IOException e) {
            e.printStackTrace();
            return new RestartData();
        }
    }

    public static void saveRestartConfig(RestartData.Config config) {
        try {
            if (!RESTART_CONFIG_FILE.exists()) {
                RESTART_CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(RESTART_CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static RestartData.Config loadRestartConfig() {
        if (!RESTART_CONFIG_FILE.exists()) {
            RestartData.Config config = new RestartData.Config();
            saveRestartConfig(config); // Create default config
            return config;
        }

        try (FileReader reader = new FileReader(RESTART_CONFIG_FILE)) {
            RestartData.Config config = GSON.fromJson(reader, RestartData.Config.class);
            return config != null ? config : new RestartData.Config();
        } catch (IOException e) {
            e.printStackTrace();
            return new RestartData.Config();
        }
    }

    // ==================== Announcement Schedules ====================

    public static void saveAnnouncementData(AnnouncementData data) {
        try {
            if (!ANNOUNCEMENT_DATA_FILE.exists()) {
                ANNOUNCEMENT_DATA_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(ANNOUNCEMENT_DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AnnouncementData loadAnnouncementData() {
        if (!ANNOUNCEMENT_DATA_FILE.exists()) {
            return new AnnouncementData();
        }

        try (FileReader reader = new FileReader(ANNOUNCEMENT_DATA_FILE)) {
            AnnouncementData data = GSON.fromJson(reader, AnnouncementData.class);
            return data != null ? data : new AnnouncementData();
        } catch (IOException e) {
            e.printStackTrace();
            return new AnnouncementData();
        }
    }

    public static void saveAnnouncementConfig(AnnouncementData.Config config) {
        try {
            if (!ANNOUNCEMENT_CONFIG_FILE.exists()) {
                ANNOUNCEMENT_CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(ANNOUNCEMENT_CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AnnouncementData.Config loadAnnouncementConfig() {
        if (!ANNOUNCEMENT_CONFIG_FILE.exists()) {
            AnnouncementData.Config config = new AnnouncementData.Config();
            saveAnnouncementConfig(config); // Create default config
            return config;
        }

        try (FileReader reader = new FileReader(ANNOUNCEMENT_CONFIG_FILE)) {
            AnnouncementData.Config config = GSON.fromJson(reader, AnnouncementData.Config.class);
            return config != null ? config : new AnnouncementData.Config();
        } catch (IOException e) {
            e.printStackTrace();
            return new AnnouncementData.Config();
        }
    }

    // ==================== Ban Config ====================

    public static void saveBanConfig(BanConfig config) {
        try {
            if (!BAN_CONFIG_FILE.exists()) {
                BAN_CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(BAN_CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static BanConfig loadBanConfig() {
        if (!BAN_CONFIG_FILE.exists()) {
            BanConfig config = new BanConfig();
            saveBanConfig(config); // Create default config
            return config;
        }

        try (FileReader reader = new FileReader(BAN_CONFIG_FILE)) {
            BanConfig config = GSON.fromJson(reader, BanConfig.class);
            return config != null ? config : new BanConfig();
        } catch (IOException e) {
            e.printStackTrace();
            return new BanConfig();
        }
    }

    // ==================== Mutes ====================

    public static void saveMuteData(MuteData data) {
        try {
            if (!MUTE_DATA_FILE.exists()) {
                MUTE_DATA_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(MUTE_DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MuteData loadMuteData() {
        if (!MUTE_DATA_FILE.exists()) {
            return new MuteData();
        }

        try (FileReader reader = new FileReader(MUTE_DATA_FILE)) {
            MuteData data = GSON.fromJson(reader, MuteData.class);

            if (data == null) {
                return new MuteData();
            }

            if (data.mutes == null) {
                data.mutes = new java.util.ArrayList<>();
            }

            return data;
        } catch (IOException e) {
            e.printStackTrace();
            return new MuteData();
        }
    }

    public static void saveMuteConfig(MuteConfig config) {
        try {
            if (!MUTE_CONFIG_FILE.exists()) {
                MUTE_CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(MUTE_CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MuteConfig loadMuteConfig() {
        if (!MUTE_CONFIG_FILE.exists()) {
            MuteConfig config = new MuteConfig();
            saveMuteConfig(config); // Create default config
            return config;
        }

        try (FileReader reader = new FileReader(MUTE_CONFIG_FILE)) {
            MuteConfig config = GSON.fromJson(reader, MuteConfig.class);
            return config != null ? config : new MuteConfig();
        } catch (IOException e) {
            e.printStackTrace();
            return new MuteConfig();
        }
    }

    // ==================== Freeze ====================

    public static void saveFreezeConfig(FreezeConfig config) {
        try {
            if (!FREEZE_CONFIG_FILE.exists()) {
                FREEZE_CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(FREEZE_CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static FreezeConfig loadFreezeConfig() {
        if (!FREEZE_CONFIG_FILE.exists()) {
            FreezeConfig config = new FreezeConfig();
            saveFreezeConfig(config); // Create default config
            return config;
        }

        try (FileReader reader = new FileReader(FREEZE_CONFIG_FILE)) {
            FreezeConfig config = GSON.fromJson(reader, FreezeConfig.class);

            if (config == null) {
                return new FreezeConfig();
            }

            if (config.instructions == null) {
                config.instructions = new FreezeConfig().instructions;
            }

            return config;
        } catch (IOException e) {
            e.printStackTrace();
            return new FreezeConfig();
        }
    }
}
