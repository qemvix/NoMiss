package com.nomiss.client;

import com.nomiss.client.gui.NoMissScreen;
import com.nomiss.config.NoMissConfig;
import com.nomiss.config.WeaponSelection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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
    public static KeyMapping addHeldKey;
    public static KeyMapping toggleStatusKey;
    public static KeyMapping cycleProfileKey;
    private static String serverContext;

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
        if (config.migratedLegacy() && Files.exists(CONFIG_PATH)) {
            try { Files.copy(CONFIG_PATH, CONFIG_PATH.resolveSibling("nomiss.before-1.2-" + java.util.UUID.randomUUID() + ".json")); }
            catch (IOException e) { canSave = false; LOGGER.error("Cannot back up the pre-1.2 settings; saving disabled to preserve them", e); }
        }
        saveConfig();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nomiss", "controls"));
        // Numeric key constants are inlined by javac, but 26.2 uses GLFW codes and 26.3
        // uses SDL scancodes. Resolve persistent key names through the running game instead.
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.menu", InputConstants.getKey("key.keyboard.semicolon").getValue(), category));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.toggle", InputConstants.getKey("key.keyboard.apostrophe").getValue(), category));
        addHeldKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.add_held", InputConstants.UNKNOWN.getValue(), category));
        toggleStatusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.toggle_status", InputConstants.UNKNOWN.getValue(), category));
        cycleProfileKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nomiss.cycle_profile", InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            String server = client.player != null && client.getCurrentServer() != null ? NoMissConfig.serverKey(client.getCurrentServer().ip) : null;
            if (!java.util.Objects.equals(serverContext, server)) {
                config.connectServer(server); serverContext = server; saveConfig();
            }
            NoMissHud.tick(client);
            // Drain clicks even in menus: typing in chat/search must never toggle the mod later.
            while (toggleKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    config.setEnabled(!config.enabled());
                    boolean saved = saveConfig();
                    client.player.sendOverlayMessage(saved ? enabledLabel() : Component.translatable("nomiss.save_failed"));
                }
            }
            while (addHeldKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) client.player.sendOverlayMessage(addHeldItem());
            }
            while (toggleStatusKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    config.setOption(NoMissConfig.Option.STATUS, !config.showStatus());
                    boolean saved = saveConfig();
                    client.player.sendOverlayMessage(saved ? Component.translatable("nomiss.status",
                        Component.translatable(config.showStatus() ? "options.on" : "options.off"))
                        : Component.translatable("nomiss.save_failed"));
                }
            }
            while (cycleProfileKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    config.cycleProfile(); boolean saved = saveConfig();
                    client.player.sendOverlayMessage(saved ? Component.translatable("nomiss.profile.active", config.activeProfile().name()) : Component.translatable("nomiss.save_failed"));
                }
            }
            while (openMenuKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) client.gui.setScreen(new NoMissScreen(null));
            }
        });
    }

    public static NoMissConfig config() { return config; }

    public static Component addHeldItem() { return addHeldItem(false); }

    public static Component addHeldItem(boolean exact) {
        var player = Minecraft.getInstance().player;
        if (player == null) return Component.translatable("nomiss.add_held.no_world");
        var held = player.getMainHandItem();
        if (held.isEmpty()) return Component.translatable("nomiss.add_held.empty");
        var captured = exact ? WeaponMatcher.exactSelection(held) : java.util.Optional.of(WeaponSelection.item(WeaponMatcher.id(held)));
        if (captured.isEmpty()) return Component.translatable("nomiss.exact.failed");
        var selection = captured.get();
        return switch (config.addSelection(selection)) {
            case ADDED -> saveConfig() ? Component.translatable("nomiss.add_held.success", held.getHoverName()) : Component.translatable("nomiss.save_failed");
            case ALREADY_SELECTED -> Component.translatable("nomiss.selection.duplicate");
            case FULL -> Component.translatable("nomiss.limit", NoMissConfig.MAX_ITEMS);
            case INVALID -> Component.translatable("nomiss.add_held.empty");
        };
    }

    public static void activateProfile(String id) { config.activateProfile(id); saveConfig(); }

    /** Parse before confirmation; preserve the existing file before committing a complete replacement. */
    public static void importSettings(NoMissConfig replacement) throws IOException {
        if (!canSave) throw new IOException("Saving is disabled; the current configuration could not be preserved");
        if (Files.exists(CONFIG_PATH)) Files.copy(CONFIG_PATH, CONFIG_PATH.resolveSibling("nomiss.before-import-" + java.util.UUID.randomUUID() + ".json"));
        replacement.save(CONFIG_PATH);
        config = replacement;
        config.connectServer(serverContext);
    }

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
