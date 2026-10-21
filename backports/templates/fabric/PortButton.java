package com.nomiss.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The old native button with the builder and tooltip interface used by the shared menus. */
public class PortButton extends Button {
    private PortTooltip tooltip;
    private PortButton(int x, int y, int width, int height, Component text, OnPress press) {
        super(x, y, width, height, text, press);
    }
    public static Builder builder(Component text, OnPress press) { return new Builder(text, press); }
    public void setX(int value) { x = value; }
    public void setY(int value) { y = value; }
    public void setTooltip(PortTooltip value) { tooltip = value; }
    @Override public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        super.render(pose, mouseX, mouseY, delta);
        var client = Minecraft.getInstance();
        if (isHoveredOrFocused() && tooltip != null && client.screen != null)
            client.screen.renderTooltip(pose, tooltip.text(), mouseX, mouseY);
    }
    public static final class Builder {
        private final Component text;
        private final OnPress press;
        private int x, y, width = 150, height = 20;
        private PortTooltip tooltip;
        Builder(Component text, OnPress press) { this.text = text; this.press = press; }
        public Builder bounds(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Builder tooltip(PortTooltip tooltip) { this.tooltip=tooltip; return this; }
        public PortButton build() { var button = new PortButton(x,y,width,height,text,press); button.tooltip=tooltip; return button; }
    }
}
