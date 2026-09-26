package crqzycat.maintena.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import crqzycat.maintena.maintenance.MaintenanceData;
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
}
