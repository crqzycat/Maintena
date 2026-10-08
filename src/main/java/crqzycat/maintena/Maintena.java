package crqzycat.maintena;

import crqzycat.maintena.disguise.DisguiseManager;
import crqzycat.maintena.event.DisguiseHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import crqzycat.maintena.maintenance.MaintenanceManager;
import crqzycat.maintena.restart.RestartManager;
import crqzycat.maintena.announcement.AnnouncementManager;
import crqzycat.maintena.command.MaintenanceCommandHandler;
import crqzycat.maintena.command.MaintenaCommandHandler;
import crqzycat.maintena.event.LoginEventHandler;
import crqzycat.maintena.event.ChatMuteHandler;
import crqzycat.maintena.event.VanishHandler;
import crqzycat.maintena.event.FreezeHandler;

public class Maintena implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register commands
        MaintenanceCommandHandler.register();
        MaintenaCommandHandler.register();

        // Register login event handler to kick non-whitelisted players
        LoginEventHandler.register();

        // Register chat mute handler (blocks chat/msg/me for muted players, commands still work)
        ChatMuteHandler.register();

        // Register vanish handler (tab list fix on join, actionbar hint)
        VanishHandler.register();

        // Register freeze handler (blocks interactions, resets movement attempts, actionbar hint)
        FreezeHandler.register();

        DisguiseManager.getInstance().initialize();
        DisguiseHandler.register();

        // Initialize manager on server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MaintenanceManager.getInstance().setServer(server);
            RestartManager.getInstance().setServer(server);
            AnnouncementManager.getInstance().setServer(server);
        });
    }
}