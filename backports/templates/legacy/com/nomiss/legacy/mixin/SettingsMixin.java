package com.nomiss.legacy.mixin;
import com.nomiss.legacy.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.SettingsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SettingsScreen.class)
public abstract class SettingsMixin extends Screen {
    @Inject(method = "init", at = @At("TAIL"))
    private void nomiss$settings(CallbackInfo ci) {
        buttons.add(new ButtonWidget(12001, 4, height - 48, 100, 20, "NoMiss settings"));
        buttons.add(new ButtonWidget(12002, 4, height - 24, 100, 20, "NoMiss: " + (LegacyClient.config().enabled() ? "ON" : "OFF")));
    }
    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void nomiss$click(ButtonWidget button, CallbackInfo ci) {
        if (button.id == 12001) { client.setScreen(new LegacyScreen(this)); ci.cancel(); }
        if (button.id == 12002) {
            LegacyClient.config().setEnabled(!LegacyClient.config().enabled()); LegacyClient.save();
            button.message = "NoMiss: " + (LegacyClient.config().enabled() ? "ON" : "OFF"); ci.cancel();
        }
    }
}
