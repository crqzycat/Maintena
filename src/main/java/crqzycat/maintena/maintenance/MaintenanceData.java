package crqzycat.maintena.maintenance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaintenanceData {
    public boolean enabled = false;
    public Set<String> whitelistedPlayers = new HashSet<>();
    public long endTime = -1; // -1 = no auto-stop set
    
    public static class Config {
        public String kickMessage = "§cServer is currently under maintenance!\n§fPlease try again later.";
        public String broadcastStart = "§6[§cMaintenance§6] §cServer entering maintenance mode.";
        public String broadcastEnd = "§6[§cMaintenance§6] §aServer maintenance complete!";
        public String broadcastWarning = "§6[§cMaintenance§6] §eServer will exit maintenance in §b%time% §eminutes.";
        public String statusMessage = "§6=== Maintenance Status ===\n" +
                "§eEnabled: §b%enabled%\n" +
                "§eWhitelisted Players: §b%players%\n" +
                "§eEnd Time: §b%endtime%";

        // Shown in the server list (multiplayer screen) MOTD while maintenance is active
        public String motdMaintenanceLine = "§c⚠ Under Maintenance";
        public String motdMaintenanceTimed = "§eBack online in §b%time%";
        public String motdMaintenanceNoTime = "§eNo estimated time given";
    }
    
    public void addWhitelistedPlayer(String player) {
        whitelistedPlayers.add(player);
    }
    
    public void removeWhitelistedPlayer(String player) {
        whitelistedPlayers.remove(player);
    }
    
    public boolean isWhitelisted(String player) {
        return whitelistedPlayers.contains(player);
    }
    
    public void clearWhitelist() {
        whitelistedPlayers.clear();
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public long getTimeRemaining() {
        if (endTime == -1) return -1;
        return endTime - System.currentTimeMillis();
    }
    
    public boolean isTimeExpired() {
        if (endTime == -1) return false;
        return System.currentTimeMillis() >= endTime;
    }
    
    public void clearEndTime() {
        endTime = -1;
    }
}
