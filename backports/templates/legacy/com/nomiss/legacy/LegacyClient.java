package com.nomiss.legacy;

import com.nomiss.config.*;
import com.nomiss.legacy.mixin.MinecraftAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.ItemStack;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class LegacyClient implements ClientModInitializer {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("nomiss.json");
    private static NoMissConfig config = new NoMissConfig();
    private static boolean canSave = true;
    private static String serverContext;
    public static KeyBinding[] keys;
    public static final String[] KEY_NAMES = {"Open settings", "Toggle NoMiss", "Add held item", "Toggle status", "Next profile"};
    @Override public void onInitializeClient() {
        try { config = NoMissConfig.load(PATH); }
        catch (IOException e) {
            if (Files.exists(PATH)) try { Files.move(PATH, PATH.resolveSibling("nomiss.broken-" + UUID.randomUUID() + ".json")); }
            catch (IOException backup) { canSave = false; }
            System.err.println("NoMiss: cannot load settings: " + e.getMessage());
        }
        if (config.migratedLegacy() && Files.exists(PATH)) try {
            Files.copy(PATH, PATH.resolveSibling("nomiss.before-1.2-" + UUID.randomUUID() + ".json"));
        } catch (IOException e) { canSave = false; }
        save();
    }
    public static NoMissConfig config() { return config; }
    public static boolean save() {
        if (!canSave) return false;
        try { config.save(PATH); return true; }
        catch (IOException e) { System.err.println("NoMiss: cannot save settings: " + e.getMessage()); return false; }
    }
    public static void importSettings(NoMissConfig replacement) throws IOException {
        if (!canSave) throw new IOException("Saving disabled; original settings could not be preserved");
        if (Files.exists(PATH)) Files.copy(PATH, PATH.resolveSibling("nomiss.before-import-" + UUID.randomUUID() + ".json"));
        replacement.save(PATH); config = replacement; config.connectServer(serverContext);
    }
    public static void registerKeys(GameOptions options) {
        keys = new KeyBinding[] {
            new KeyBinding("key.nomiss.menu", 39, "key.categories.nomiss"),
            new KeyBinding("key.nomiss.toggle", 40, "key.categories.nomiss"),
            new KeyBinding("key.nomiss.add_held", 0, "key.categories.nomiss"),
            new KeyBinding("key.nomiss.toggle_status", 0, "key.categories.nomiss"),
            new KeyBinding("key.nomiss.cycle_profile", 0, "key.categories.nomiss")
        };
        var all = Arrays.copyOf(options.allKeys, options.allKeys.length + keys.length);
        System.arraycopy(keys, 0, all, options.allKeys.length, keys.length); options.allKeys = all;
        options.load(); KeyBinding.updateKeysByCode();
    }
    public static ItemStack held() {
        var client = MinecraftClient.getInstance(); return client.player == null ? null : client.player.getMainHandStack();
    }
    public static void tick() {
        var client = MinecraftClient.getInstance();
        var info = ((MinecraftAccessor) client).nomiss$server();
        String server = client.player != null && info != null ? NoMissConfig.serverKey(info.address) : null;
        if (!Objects.equals(serverContext, server)) { config.connectServer(server); serverContext = server; save(); }
        LegacyHud.tick();
        if (keys == null) return;
        for (int i = 0; i < keys.length; i++) while (keys[i].wasPressed()) {
            if (client.player == null || client.currentScreen != null) continue;
            switch (i) {
                case 0 -> client.setScreen(new LegacyScreen(null));
                case 1 -> { config.setEnabled(!config.enabled()); LegacyHud.message(save() ? "NoMiss: " + (config.enabled() ? "ON" : "OFF") : "Could not save settings"); }
                case 2 -> LegacyHud.message(addHeld(false));
                case 3 -> { config.setOption(NoMissConfig.Option.STATUS, !config.showStatus()); save(); }
                case 4 -> { config.cycleProfile(); save(); LegacyHud.message("Profile: " + config.activeProfile().name()); }
            }
        }
    }
    public static String addHeld(boolean exact) {
        var stack = held(); if (stack == null) return "Hold an item in a world first";
        var candidate = exact ? LegacyMatcher.exact(stack) : Optional.of(WeaponSelection.item(LegacyMatcher.id(stack)));
        if (candidate.isEmpty()) return "Could not capture this item";
        return switch (config.addSelection(candidate.get())) {
            case ADDED -> save() ? "Added " + stack.getName() : "Could not save settings";
            case ALREADY_SELECTED -> "Already selected";
            case FULL -> "All 20 slots are occupied";
            case INVALID -> "Invalid selection";
        };
    }
    public static boolean blockAttack() {
        var client = MinecraftClient.getInstance();
        if (!config.enabled() || client.player == null || client.result == null) return false;
        var rules = LegacyMatcher.resolve(held());
        // Pre-1.9 has no attack cooldown. Ignore imported cooldown rules as well.
        var reason = AttackProtection.blockedReason(config.enabled(), rules, LegacyTarget.fromName(client.result.type.name()), 1F);
        if (reason != null) LegacyHud.blocked(reason);
        return reason != null;
    }
    public static boolean blockMining() {
        var client = MinecraftClient.getInstance();
        if (!config.enabled() || client.player == null || client.result == null) return false;
        var rules = LegacyMatcher.resolve(held());
        boolean block = rules != null && !rules.blocks() && LegacyTarget.fromName(client.result.type.name()) == AttackProtection.Target.BLOCK;
        if (block) LegacyHud.blocked(AttackProtection.BlockReason.BLOCKS_DISABLED);
        return block;
    }
}
