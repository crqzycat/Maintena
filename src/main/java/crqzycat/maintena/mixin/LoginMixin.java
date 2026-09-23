package crqzycat.maintena.mixin;

import crqzycat.maintena.maintenance.MaintenanceManager;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class LoginMixin {
    
    @Shadow
    public abstract void disconnect(Text reason);
    
    @Shadow
    public GameProfile profile;
    
    @Inject(method = "onGameProfileFetched", at = @At("HEAD"), cancellable = true)
    private void onGameProfileFetched(CallbackInfo ci) {
        MaintenanceManager manager = MaintenanceManager.getInstance();
        
        if (manager.isEnabled() && profile != null) {
            String playerName = profile.getName();
            
            if (!manager.isWhitelisted(playerName)) {
                String kickMessage = manager.getConfig().kickMessage;
                disconnect(Text.of(kickMessage));
                ci.cancel();
            }
        }
    }
}
