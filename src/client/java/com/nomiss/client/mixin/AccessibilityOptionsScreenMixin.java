package com.nomiss.client.mixin;

import com.nomiss.client.NoMissClient;
import com.nomiss.client.gui.NoMissScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AccessibilityOptionsScreen.class)
public abstract class AccessibilityOptionsScreenMixin extends OptionsSubScreen {
    @Unique private Button nomiss$toggle;
    protected AccessibilityOptionsScreenMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @Override
    public void tick() {
        super.tick();
        if (nomiss$toggle != null) nomiss$toggle.setMessage(NoMissClient.enabledLabel());
    }

    @Inject(method = "addOptions", at = @At("HEAD"))
    private void nomiss$addSettings(CallbackInfo ci) {
        Button toggle = Button.builder(NoMissClient.enabledLabel(), button -> {
            NoMissClient.config().setEnabled(!NoMissClient.config().enabled());
            boolean saved = NoMissClient.saveConfig();
            button.setMessage(NoMissClient.enabledLabel());
            button.setTooltip(Tooltip.create(Component.translatable(saved ? "nomiss.enabled.tooltip" : "nomiss.save_failed")));
        }).build();
        nomiss$toggle = toggle;
        toggle.setTooltip(Tooltip.create(Component.translatable("nomiss.enabled.tooltip")));
        Button settings = Button.builder(Component.translatable("nomiss.settings"), button ->
            minecraft.gui.setScreen(new NoMissScreen(this))).build();
        list.addSmall(toggle, settings);
    }
}
