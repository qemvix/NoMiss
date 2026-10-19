package com.nomiss.client;

import com.nomiss.config.AttackProtection.BlockReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small, independent indicators that never replace chat, titles or the action bar. */
public final class NoMissHud {
    private static BlockReason lastReason;
    private static int remainingTicks;

    private NoMissHud() {}

    public static void blocked(BlockReason reason) {
        if (!NoMissClient.config().showBlockedAttacks()) return;
        lastReason = reason;
        remainingTicks = 30;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || !NoMissClient.config().enabled() || !NoMissClient.config().showBlockedAttacks()) {
            remainingTicks = 0;
        } else if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()) return;
        var config = NoMissClient.config();
        int y = 6;
        if (config.showStatus()) {
            draw(graphics, client, NoMissClient.enabledLabel(), y, config.enabled() ? 0xB0205020 : 0xB0602020);
            y += 14;
        }
        if (config.enabled() && config.showBlockedAttacks() && remainingTicks > 0 && lastReason != null) {
            String key = switch (lastReason) {
                case OUT_OF_REACH -> "nomiss.blocked.reach";
                case COOLDOWN -> "nomiss.blocked.cooldown";
                case BLOCKS_DISABLED -> "nomiss.blocked.blocks";
            };
            draw(graphics, client, Component.translatable(key), y, 0xB0303030);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, Minecraft client, Component label, int y, int background) {
        graphics.fill(4, y - 2, 8 + client.font.width(label), y + 11, background);
        graphics.text(client.font, label, 6, y, 0xFFFFFFFF);
    }
}
