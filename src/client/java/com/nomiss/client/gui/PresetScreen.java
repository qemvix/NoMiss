package com.nomiss.client.gui;

import com.nomiss.client.NoMissClient;
import com.nomiss.config.WeaponSelection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PresetScreen extends Screen {
    private final Screen parent;
    private Component error;
    PresetScreen(Screen parent) { super(Component.translatable("nomiss.profile.preset")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(320, width - 24), x = (width - w) / 2;
        String[] names = {"Survival", "PvP", "Modpack"};
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            addRenderableWidget(Button.builder(Component.literal(name), button -> {
                try {
                    var config = NoMissClient.config(); var p = config.addProfile(name, false);
                    p.setCooldown(false); p.setBlocks(!name.equals("PvP"));
                    if (name.equals("Modpack")) p.setSlot(3, WeaponSelection.category("axes"));
                    config.activateProfile(p.id());
                    if (NoMissClient.saveConfig()) onClose(); else error = Component.translatable("nomiss.save_failed");
                } catch (IllegalArgumentException e) { error = Component.literal(e.getMessage()); }
            }).bounds(x, 54 + i * 28, w, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(x, height - 28, w, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta); g.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        g.textRenderer().acceptScrollingWithDefaultCenter(error != null ? error : Component.translatable("nomiss.preset.hint"), 12, width - 12, height - 64, height - 36);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
