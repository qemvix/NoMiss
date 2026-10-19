package com.nomiss.client;

import com.nomiss.client.gui.NoMissScreen;
import com.nomiss.config.NoMissConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NoMissClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("nomiss");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("nomiss.json");
    private static NoMissConfig config = new NoMissConfig();
    private static boolean canSave = true;
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        try {
            config = NoMissConfig.load(CONFIG_PATH);
        } catch (IOException e) {
            LOGGER.error("Cannot load NoMiss settings; using defaults", e);
            // Preserve the original before allowing any future save to replace it.
            if (Files.exists(CONFIG_PATH)) {
                try {
                    Files.move(CONFIG_PATH, CONFIG_PATH.resolveSibling("nomiss.broken-" + System.currentTimeMillis() + ".json"));
                } catch (IOException backupFailure) {
                    canSave = false;
                    LOGGER.error("Cannot preserve the original config; saving is disabled for this session", backupFailure);
                }
            }
        }
        saveConfig();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nomiss", "controls"));
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.menu", InputConstants.KEY_SEMICOLON, category));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.toggle", InputConstants.KEY_APOSTROPHE, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain clicks even in menus: typing in chat/search must never toggle the mod later.
            while (toggleKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    config.setEnabled(!config.enabled());
                    boolean saved = saveConfig();
                    client.player.sendOverlayMessage(saved ? enabledLabel() : Component.translatable("nomiss.save_failed"));
                }
            }
            while (openMenuKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) client.gui.setScreen(new NoMissScreen(null));
            }
        });
    }

    public static NoMissConfig config() { return config; }

    public static boolean saveConfig() {
        if (!canSave) return false;
        try {
            config.save(CONFIG_PATH);
            return true;
        } catch (IOException e) {
            LOGGER.error("Cannot save NoMiss settings", e);
            return false;
        }
    }

    public static Component enabledLabel() {
        return Component.translatable("nomiss.enabled", Component.translatable(config.enabled() ? "options.on" : "options.off"));
    }
}
