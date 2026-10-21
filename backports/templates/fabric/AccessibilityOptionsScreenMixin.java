package com.nomiss.client.mixin;

import com.nomiss.client.NoMissClient;
import com.nomiss.client.gui.NoMissScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AccessibilityOptionsScreen.class)
public abstract class AccessibilityOptionsScreenMixin extends Screen {
    protected AccessibilityOptionsScreenMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void nomiss$settings(CallbackInfo ci) {
        addRenderableWidget(Button.builder(NoMissClient.enabledLabel(), button -> {
            NoMissClient.config().setEnabled(!NoMissClient.config().enabled());
            boolean saved = NoMissClient.saveConfig();
            button.setMessage(saved ? NoMissClient.enabledLabel() : Component.translatable("nomiss.save_failed"));
        }).bounds(4, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nomiss.settings"), button ->
            minecraft.setScreen(new NoMissScreen(this))).bounds(4, height - 50, 100, 20).build());
    }
}
