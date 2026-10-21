package com.nomiss.client.gui;
import net.minecraft.network.chat.Component;
public record PortTooltip(Component text) {
    public static PortTooltip create(Component text) { return new PortTooltip(text); }
}
