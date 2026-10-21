package com.nomiss.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draw bounded labels on editions before the retained text renderer. */
final class CompatText {
    static void center(GuiGraphics graphics, int x, int y, Component text) {
        graphics.drawCenteredString(Minecraft.getInstance().font, text, x, y, 0xFFFFFF);
    }
    static void between(GuiGraphics graphics, Component text, int left, int right, int top, int bottom) {
        var font = Minecraft.getInstance().font;
        String value = text.getString();
        if (font.width(value) > right - left) value = font.plainSubstrByWidth(value, Math.max(0, right - left - font.width("…"))) + "…";
        graphics.drawCenteredString(font, value, (left + right) / 2, (top + bottom - 9) / 2, 0xFFFFFF);
    }
}
