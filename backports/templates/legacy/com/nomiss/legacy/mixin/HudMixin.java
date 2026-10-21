package com.nomiss.legacy.mixin;
import com.nomiss.legacy.LegacyHud;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class HudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void nomiss$hud(CallbackInfo ci) { LegacyHud.render(); }
}
