package com.nomiss.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Persistent settings, independent of Minecraft so they can be validated without a running client. */
public final class NoMissConfig {
    public static final int MAX_ITEMS = 20;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final List<String> DEFAULT_ITEMS = List.of(
        "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:copper_sword",
        "minecraft:golden_sword", "minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:netherite_sword",
        "minecraft:wooden_spear", "minecraft:stone_spear", "minecraft:copper_spear",
        "minecraft:golden_spear", "minecraft:iron_spear", "minecraft:diamond_spear", "minecraft:netherite_spear",
        "minecraft:mace");

    private boolean enabled = true;
    private boolean allowBlockBreaking = true;
    private boolean blockDuringCooldown = false;
    private final List<String> slots = new ArrayList<>(Collections.nCopies(MAX_ITEMS, ""));

    public NoMissConfig() { resetItems(); }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean allowBlockBreaking() { return allowBlockBreaking; }
    public void setAllowBlockBreaking(boolean value) { allowBlockBreaking = value; }
    public boolean blockDuringCooldown() { return blockDuringCooldown; }
    public void setBlockDuringCooldown(boolean value) { blockDuringCooldown = value; }
    public Set<String> items() {
        var result = new LinkedHashSet<>(slots);
        result.remove("");
        return Collections.unmodifiableSet(result);
    }
    public boolean contains(String id) { return !id.isEmpty() && slots.contains(id); }
    public String itemAt(int slot) { return slots.get(slot); }
    public int slotOf(String id) { return slots.indexOf(id); }

    public boolean setItem(int slot, String id) {
        if (slot < 0 || slot >= MAX_ITEMS || id == null) return false;
        if (!id.isEmpty() && (!ITEM_ID.matcher(id).matches() || (contains(id) && slotOf(id) != slot))) return false;
        slots.set(slot, id);
        return true;
    }

    public void clearItems() { Collections.fill(slots, ""); }
    public void resetItems() {
        clearItems();
        for (int i = 0; i < DEFAULT_ITEMS.size(); i++) slots.set(i, DEFAULT_ITEMS.get(i));
    }

    public static NoMissConfig load(Path path) throws IOException {
        NoMissConfig config = new NoMissConfig();
        if (!Files.exists(path)) {
            // Migrate the old sentinel once; subsequent toggles use the JSON setting.
            config.enabled = !Files.exists(path.resolveSibling("nomiss.off"));
            return config;
        }
        try (var reader = Files.newBufferedReader(path)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) throw new JsonParseException("Empty config");
            if (json.has("enabled")) config.enabled = readBoolean(json, "enabled");
            if (json.has("allowBlockBreaking")) config.allowBlockBreaking = readBoolean(json, "allowBlockBreaking");
            if (json.has("blockDuringCooldown")) config.blockDuringCooldown = readBoolean(json, "blockDuringCooldown");
            if (json.has("items")) {
                if (!json.get("items").isJsonArray()) throw new JsonParseException("items must be an array");
                config.clearItems();
                int slot = 0;
                for (var item : json.getAsJsonArray("items")) {
                    if (slot >= MAX_ITEMS) break;
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        config.setItem(slot, item.getAsString());
                    }
                    slot++;
                }
            }
            return config;
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            throw new IOException("Invalid NoMiss config: " + path, e);
        }
    }

    private static boolean readBoolean(JsonObject json, String key) {
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "nomiss-", ".tmp");
        try {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("allowBlockBreaking", allowBlockBreaking);
            json.addProperty("blockDuringCooldown", blockDuringCooldown);
            json.add("items", GSON.toJsonTree(slots));
            Files.writeString(temp, GSON.toJson(json) + System.lineSeparator());
            try {
                Files.move(temp, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
