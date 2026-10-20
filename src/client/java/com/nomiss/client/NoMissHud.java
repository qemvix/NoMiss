package com.nomiss.client;

import com.nomiss.config.AttackProtection.BlockReason;
import com.nomiss.config.NoMissConfig;
import com.nomiss.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

/** All HUD indicators share position/size/opacity, but each has its own visibility toggle. */
public final class NoMissHud {
    private static BlockReason lastReason;
    private static int remainingTicks;
    private static Component heldLabel;
    private static boolean heldProtected;
    private NoMissHud() {}
    public static void blocked(BlockReason reason) {
        if (!NoMissClient.config().showBlockedAttacks()) return;
        lastReason = reason; remainingTicks = 30;
    }
    public static void tick(Minecraft client) {
        var config = NoMissClient.config();
        if (client.player == null || !config.enabled() || !config.showBlockedAttacks()) remainingTicks = 0;
        else if (remainingTicks > 0) remainingTicks--;
        heldLabel = null;
        if (client.player != null && config.option(NoMissConfig.Option.PROTECTED_ITEM)) {
            var held = client.player.getMainHandItem();
            heldProtected = config.enabled() && !client.player.isSpectator() && WeaponMatcher.resolve(held) != null;
            heldLabel = Component.translatable(heldProtected ? "nomiss.held.protected" : "nomiss.held.unprotected", held.isEmpty() ? Component.translatable("nomiss.empty_hand") : held.getHoverName());
        }
    }
    private record Line(Component label, int background) {}
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()) return;
        var config = NoMissClient.config();
        var lines = new ArrayList<Line>();
        if (config.showStatus()) lines.add(new Line(NoMissClient.enabledLabel().copy().append(" · " + config.activeProfile().name()), config.enabled() ? 0x205020 : 0x602020));
        if (config.option(NoMissConfig.Option.PROTECTED_ITEM) && heldLabel != null) lines.add(new Line(heldLabel, heldProtected ? 0x205020 : 0x303030));
        if (config.enabled() && config.showBlockedAttacks() && remainingTicks > 0 && lastReason != null) {
            String key = switch (lastReason) { case OUT_OF_REACH -> "nomiss.blocked.reach"; case COOLDOWN -> "nomiss.blocked.cooldown"; case BLOCKS_DISABLED -> "nomiss.blocked.blocks"; };
            lines.add(new Line(Component.translatable(key), 0x303030));
        }
        if (lines.isEmpty()) return;
        var hud = config.hud(); float scale = hud.scale() / 100F;
        int screenWidth = (int)(graphics.guiWidth() / scale), screenHeight = (int)(graphics.guiHeight() / scale);
        boolean right = hud.corner() == HudSettings.Corner.TOP_RIGHT || hud.corner() == HudSettings.Corner.BOTTOM_RIGHT;
        boolean bottom = hud.corner() == HudSettings.Corner.BOTTOM_LEFT || hud.corner() == HudSettings.Corner.BOTTOM_RIGHT;
        int y = bottom ? screenHeight - 6 - lines.size() * 14 : 6;
        int alpha = Math.max(4, hud.opacity() * 255 / 100);
        graphics.pose().pushMatrix(); graphics.pose().scale(scale, scale);
        for (var line : lines) {
            // Ellipsize long custom item/profile names to keep every corner inside the screen.
            String text = line.label().getString();
            int maxWidth = screenWidth - 16;
            if (client.font.width(text) > maxWidth) text = client.font.plainSubstrByWidth(text, Math.max(1, maxWidth - client.font.width("…"))) + "…";
            int x = right ? screenWidth - 6 - client.font.width(text) : 6;
            graphics.fill(x - 2, y - 2, x + client.font.width(text) + 2, y + 11, ((alpha * 176 / 255) << 24) | line.background());
            graphics.text(client.font, text, x, y, (alpha << 24) | 0xFFFFFF); y += 14;
        }
        graphics.pose().popMatrix();
    }
}
