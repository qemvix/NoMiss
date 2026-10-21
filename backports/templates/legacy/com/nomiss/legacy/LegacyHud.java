package com.nomiss.legacy;

import com.nomiss.config.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.gui.DrawableHelper;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;

public final class LegacyHud extends DrawableHelper {
    private static int blockedTicks, messageTicks;
    private static String message;
    private static AttackProtection.BlockReason reason;
    public static void blocked(AttackProtection.BlockReason value) {
        if (LegacyClient.config().showBlockedAttacks()) { reason = value; blockedTicks = 30; }
    }
    public static void message(String value) { message = value; messageTicks = 60; }
    public static void tick() { if (blockedTicks > 0) blockedTicks--; if (messageTicks > 0) messageTicks--; }
    public static void render() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || client.options.hudHidden) return;
        var config = LegacyClient.config(); var lines = new ArrayList<String>();
        if (config.showStatus()) lines.add("NoMiss: " + (config.enabled() ? "ON" : "OFF") + " | " + config.activeProfile().name());
        if (config.option(NoMissConfig.Option.PROTECTED_ITEM)) {
            var held = LegacyClient.held();
            lines.add((config.enabled() && LegacyMatcher.resolve(held) != null ? "Protected: " : "Unprotected: ") + (held == null ? "Empty hand" : held.getName()));
        }
        if (config.enabled() && config.showBlockedAttacks() && blockedTicks > 0 && reason != null)
            lines.add(reason == AttackProtection.BlockReason.BLOCKS_DISABLED ? "Block breaking disabled" : "No entity in reach");
        if (messageTicks > 0 && message != null) lines.add(message);
        if (lines.isEmpty()) return;
        var window = new Window(client); float scale = config.hud().scale() / 100F;
        int width = (int)(window.getWidth() / scale), height = (int)(window.getHeight() / scale);
        boolean right = config.hud().corner() == HudSettings.Corner.TOP_RIGHT || config.hud().corner() == HudSettings.Corner.BOTTOM_RIGHT;
        boolean bottom = config.hud().corner() == HudSettings.Corner.BOTTOM_LEFT || config.hud().corner() == HudSettings.Corner.BOTTOM_RIGHT;
        int y = bottom ? height - 6 - lines.size() * 14 : 6;
        int alpha = Math.max(4, config.hud().opacity() * 255 / 100);
        GL11.glPushMatrix(); GL11.glScalef(scale, scale, 1F);
        for (String original : lines) {
            String line = original;
            if (client.textRenderer.getStringWidth(line) > width - 16) {
                while (!line.isEmpty() && client.textRenderer.getStringWidth(line + "...") > width - 16) line = line.substring(0, line.length() - 1);
                line += "...";
            }
            int x = right ? width - 6 - client.textRenderer.getStringWidth(line) : 6;
            fill(x - 2, y - 2, x + client.textRenderer.getStringWidth(line) + 2, y + 11, ((alpha * 176 / 255) << 24) | 0x303030);
            client.textRenderer.drawWithShadow(line, x, y, (alpha << 24) | 0xFFFFFF); y += 14;
        }
        GL11.glPopMatrix();
    }
}
