package com.nomiss.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

final class TextPromptScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<String> accept;
    private Component error;
    TextPromptScreen(Screen parent, Component title, String initial, Consumer<String> accept) {
        super(title); this.parent = parent; this.initial = initial; this.accept = accept;
    }
    @Override protected void init() {
        int w = Math.min(320, width - 24), x = (width - w) / 2;
        var input = addRenderableWidget(new EditBox(font, x, height / 2 - 28, w, 20, title));
        input.setMaxLength(48); input.setValue(initial);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            try { accept.accept(input.getValue()); onClose(); } catch (IllegalArgumentException e) { error = Component.literal(e.getMessage()); }
        }).bounds(x, height / 2, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(x, height / 2 + 24, w, 20).build());
        setInitialFocus(input);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 20, title);
        if (error != null) graphics.textRenderer().acceptScrollingWithDefaultCenter(error, 12, width - 12, height - 40, height - 20);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
