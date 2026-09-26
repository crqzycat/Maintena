package crqzycat.maintena.restart;

import java.util.ArrayList;
import java.util.List;

public class RestartData {

    public List<RestartSchedule> schedules = new ArrayList<>();

    public static class Config {
        // Minuten vor dem Restart, zu denen jeweils eine Warnung gebroadcastet wird
        public List<Integer> warningMinutes = new ArrayList<>(List.of(30, 15, 10, 5, 3, 2, 1));

        public String broadcastWarning = "§6[§cRestart§6] §eServer restarts in §b%time% §eminute(s)!";
        public String broadcastSaving = "§6[§cRestart§6] §eSaving the world...";
        public String broadcastRestartNow = "§6[§cRestart§6] §cServer is restarting now!";
        public String broadcastCancelled = "§6[§cRestart§6] §aScheduled restart was cancelled.";

        public String scheduleListHeader = "§6=== Scheduled Restarts ===";
        public String scheduleListEmpty = "§eNo scheduled restarts configured.";
    }
}
