package com.nomiss.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Versioned local configuration. Only active profile rules affect combat; display options are global. */
public final class NoMissConfig {
    public static final int MAX_ITEMS = 20;
    public static final int MAX_PROFILES = 32;
    public static final int MAX_JSON_LENGTH = 524288;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public enum Option {
        ENABLED("enabled", "nomiss.enabled", true), COOLDOWN("blockDuringCooldown", "nomiss.cooldown", false),
        BLOCKS("allowBlockBreaking", "nomiss.blocks", true), STATUS("showStatus", "nomiss.status", true),
        BLOCKED_HINTS("showBlockedAttacks", "nomiss.hints", false), PROTECTED_ITEM("showProtectedItem", "nomiss.protected", false),
        AUTO_SERVER("automaticServerProfiles", "nomiss.auto_server", false);
        public final String jsonKey, translationKey;
        public final boolean defaultValue;
        Option(String jsonKey, String translationKey, boolean defaultValue) { this.jsonKey = jsonKey; this.translationKey = translationKey; this.defaultValue = defaultValue; }
    }
    public enum AddResult { ADDED, ALREADY_SELECTED, FULL, INVALID }
    public record Rules(int slot, WeaponSelection selection, boolean cooldown, boolean blocks) {}
    private final EnumMap<Option, Boolean> options = new EnumMap<>(Option.class);
    private final List<NoMissProfile> profiles = new ArrayList<>();
    private final Map<String, String> servers = new LinkedHashMap<>();
    private final HudSettings hud = new HudSettings();
    private String activeId;
    private transient String currentServer, returnProfile;
    private transient boolean serverScoped;
    private transient boolean migratedLegacy;
    public boolean migratedLegacy() { return migratedLegacy; }

    public NoMissConfig() {
        for (Option option : Option.values()) options.put(option, option.defaultValue);
        var survival = new NoMissProfile("survival", "Survival"); survival.defaultWeapons(); profiles.add(survival);
        var pvp = survival.copy("pvp", "PvP"); pvp.setCooldown(false); pvp.setBlocks(false); profiles.add(pvp);
        var modpack = survival.copy("modpack", "Modpack"); modpack.setSlot(3, WeaponSelection.category("axes")); profiles.add(modpack);
        activeId = survival.id();
    }
    public NoMissProfile activeProfile() { return profiles.stream().filter(p -> p.id().equals(activeId)).findFirst().orElseThrow(); }
    public List<NoMissProfile> profiles() { return List.copyOf(profiles); }
    public HudSettings hud() { return hud; }
    public boolean option(Option option) { return switch (option) { case COOLDOWN -> activeProfile().cooldown(); case BLOCKS -> activeProfile().blocks(); default -> options.get(option); }; }
    public void setOption(Option option, boolean value) {
        switch (option) { case COOLDOWN -> activeProfile().setCooldown(value); case BLOCKS -> activeProfile().setBlocks(value); default -> {
            boolean previous = options.put(option, value);
            if (option == Option.AUTO_SERVER && value && !previous && currentServer != null) {
                returnProfile = activeId;
                String mapped = servers.get(currentServer);
                if (mapped != null) activeId = mapped;
                rememberServer(); serverScoped = true;
            } else if (option == Option.AUTO_SERVER && !value && previous) {
                serverScoped = false; returnProfile = activeId;
            }
        } }
    }
    public void resetOption(Option option) { setOption(option, option.defaultValue); }
    public boolean enabled() { return option(Option.ENABLED); }
    public void setEnabled(boolean value) { setOption(Option.ENABLED, value); }
    public boolean allowBlockBreaking() { return option(Option.BLOCKS); }
    public void setAllowBlockBreaking(boolean value) { setOption(Option.BLOCKS, value); }
    public boolean blockDuringCooldown() { return option(Option.COOLDOWN); }
    public void setBlockDuringCooldown(boolean value) { setOption(Option.COOLDOWN, value); }
    public boolean showStatus() { return option(Option.STATUS); }
    public boolean showBlockedAttacks() { return option(Option.BLOCKED_HINTS); }
    public WeaponSelection selection(int slot) { return activeProfile().slot(slot); }
    public boolean setSelection(int slot, WeaponSelection selection) { return activeProfile().setSlot(slot, selection); }
    public String itemAt(int slot) { var s = selection(slot); return s.kind() == WeaponSelection.Kind.ITEM || s.kind() == WeaponSelection.Kind.EXACT ? s.value() : s.key(); }
    public int slotOf(String id) { for (int i = 0; i < MAX_ITEMS; i++) if (selection(i).kind() == WeaponSelection.Kind.ITEM && selection(i).value().equals(id)) return i; return -1; }
    public Set<String> items() { var items = new LinkedHashSet<String>(); for (var s : activeProfile().slots()) if (!s.isEmpty()) items.add(s.key()); return Collections.unmodifiableSet(items); }
    public boolean setItem(int slot, String id) { try { return id != null && setSelection(slot, WeaponSelection.item(id)); } catch (IllegalArgumentException e) { return false; } }
    public void clearItems() { activeProfile().clear(); }
    public void resetItems() { activeProfile().defaultWeapons(); }
    public boolean contains(String id) { return resolve(id, vanillaCategories(id), "") != null; }
    /** Used for the registry-free legacy API; actual gameplay supplies live item tags. */
    public static Set<String> vanillaCategories(String id) {
        if (id == null || !id.startsWith("minecraft:")) return Set.of();
        if (id.endsWith("_sword")) return Set.of("swords");
        if (id.endsWith("_spear")) return Set.of("spears");
        if (id.equals("minecraft:mace")) return Set.of("maces");
        if (id.endsWith("_pickaxe")) return Set.of("pickaxes");
        if (id.endsWith("_axe")) return Set.of("axes");
        if (id.endsWith("_shovel")) return Set.of("shovels");
        if (id.endsWith("_hoe")) return Set.of("hoes");
        return Set.of();
    }
    public Rules resolve(String id, Set<String> categories, String fingerprint) {
        WeaponSelection best = null; int slot = -1;
        for (int i = 0; i < MAX_ITEMS; i++) {
            var candidate = selection(i);
            if (candidate.matches(id, categories, fingerprint) && (best == null || candidate.priority() > best.priority())) { best = candidate; slot = i; }
        }
        return best == null ? null : new Rules(slot, best, best.cooldown().resolve(blockDuringCooldown()), best.blocks().resolve(allowBlockBreaking()));
    }
    public AddResult addHeldItem(String id) {
        try { return addSelection(WeaponSelection.item(id)); } catch (IllegalArgumentException | NullPointerException e) { return AddResult.INVALID; }
    }
    public AddResult addSelection(WeaponSelection candidate) {
        if (candidate == null || candidate.isEmpty()) return AddResult.INVALID;
        for (var s : activeProfile().slots()) if (candidate.key().equals(s.key())) return AddResult.ALREADY_SELECTED;
        for (int i = 0; i < MAX_ITEMS; i++) if (selection(i).isEmpty()) { setSelection(i, candidate); return AddResult.ADDED; }
        return AddResult.FULL;
    }
    public NoMissProfile addProfile(String name, boolean duplicate) {
        if (profiles.size() >= MAX_PROFILES) throw new IllegalArgumentException("Maximum of 32 profiles");
        var profile = duplicate ? activeProfile().copy(UUID.randomUUID().toString(), name) : new NoMissProfile(UUID.randomUUID().toString(), name);
        if (!duplicate) profile.defaultWeapons(); profiles.add(profile); return profile;
    }
    public boolean activateProfile(String id) {
        if (profiles.stream().noneMatch(p -> p.id().equals(id))) return false;
        activeId = id;
        if (currentServer != null && option(Option.AUTO_SERVER)) { rememberServer(); serverScoped = true; }
        return true;
    }
    public void cycleProfile() { int index = profiles.indexOf(activeProfile()); activateProfile(profiles.get((index + 1) % profiles.size()).id()); }
    public boolean deleteProfile(String id) {
        if (profiles.size() == 1 || profiles.stream().noneMatch(p -> p.id().equals(id))) return false;
        profiles.removeIf(p -> p.id().equals(id)); servers.values().removeIf(id::equals);
        if (activeId.equals(id)) activeId = profiles.getFirst().id();
        if (Objects.equals(returnProfile, id)) returnProfile = profiles.getFirst().id();
        return true;
    }
    public static String serverKey(String address) {
        if (address == null || address.isBlank() || address.length() > 255 || address.chars().anyMatch(Character::isISOControl)) return null;
        String key = address.strip().toLowerCase(Locale.ROOT);
        return key.endsWith(":25565") ? key.substring(0, key.length() - 6) : key;
    }
    public String currentServer() { return currentServer; }
    public Map<String, String> serverProfiles() { return Collections.unmodifiableMap(servers); }
    public void connectServer(String address) {
        String key = serverKey(address);
        if (Objects.equals(currentServer, key)) return;
        disconnectServer(); currentServer = key; returnProfile = activeId;
        if (key != null && option(Option.AUTO_SERVER)) {
            String mapped = servers.get(key);
            if (mapped != null) activeId = mapped;
            rememberServer(); serverScoped = true;
        }
    }
    public void rememberServer() {
        if (currentServer == null) return;
        if (servers.size() >= 256 && !servers.containsKey(currentServer)) return;
        servers.put(currentServer, activeId);
    }
    public void forgetServer() { if (currentServer != null) servers.remove(currentServer); }
    public void disconnectServer() {
        if (serverScoped && returnProfile != null && profiles.stream().anyMatch(p -> p.id().equals(returnProfile))) activeId = returnProfile;
        currentServer = returnProfile = null; serverScoped = false;
    }
    public static NoMissConfig load(Path path) throws IOException {
        if (!Files.exists(path)) { var config = new NoMissConfig(); config.setEnabled(!Files.exists(path.resolveSibling("nomiss.off"))); return config; }
        if (Files.size(path) > MAX_JSON_LENGTH) throw new IOException("NoMiss config is too large");
        return parse(Files.readString(path), false);
    }
    public static NoMissConfig parse(String text, boolean imported) throws IOException {
        try {
            if (text == null || text.length() > MAX_JSON_LENGTH || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_LENGTH)
                throw new JsonParseException("Settings are empty or too large");
            var json = JsonParser.parseString(text).getAsJsonObject();
            var config = new NoMissConfig();
            int version = json.has("schemaVersion") ? integer(json, "schemaVersion") : 1;
            if (version != 1 && version != 2) throw new JsonParseException("Unsupported NoMiss settings version");
            for (Option option : Option.values()) if (json.has(option.jsonKey)) config.setOption(option, bool(json, option.jsonKey));
            if (version == 1) {
                config.migratedLegacy = true;
                if (json.has("items")) {
                    config.clearItems(); var items = json.getAsJsonArray("items"); int i = 0;
                    for (var item : items) { if (i == MAX_ITEMS) break; if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) config.setItem(i, item.getAsString()); i++; }
                }
                // Old selections remain in exactly their original numbered slots.
                config.activeProfile().rename("Migrated settings");
            } else {
                config.profiles.clear(); var ids = new HashSet<String>();
                var profiles = json.getAsJsonArray("profiles");
                if (profiles.isEmpty() || profiles.size() > MAX_PROFILES) throw new JsonParseException("Use 1–32 profiles");
                for (var element : profiles) {
                    var p = element.getAsJsonObject(); var profile = new NoMissProfile(string(p, "id"), string(p, "name"));
                    if (!ids.add(profile.id())) throw new JsonParseException("Duplicate profile ID");
                    profile.setCooldown(bool(p, "cooldown")); profile.setBlocks(bool(p, "blocks"));
                    var slots = p.getAsJsonArray("slots"); if (slots.size() != MAX_ITEMS) throw new JsonParseException("Profiles must have 20 slots");
                    for (int i = 0; i < MAX_ITEMS; i++) {
                        var s = slots.get(i).getAsJsonObject();
                        var selection = new WeaponSelection(WeaponSelection.Kind.valueOf(string(s, "kind")), string(s, "value"), string(s, "fingerprint"), string(s, "label"),
                            WeaponSelection.Override.valueOf(string(s, "cooldown")), WeaponSelection.Override.valueOf(string(s, "blocks")));
                        if (!profile.setSlot(i, selection)) throw new JsonParseException("Duplicate selection");
                    }
                    config.profiles.add(profile);
                }
                config.activeId = string(json, "activeProfile");
                if (!ids.contains(config.activeId)) throw new JsonParseException("Missing active profile");
                var hud = json.getAsJsonObject("hud");
                config.hud.setCorner(HudSettings.Corner.valueOf(string(hud, "corner")));
                config.hud.setScale(integer(hud, "scale")); config.hud.setOpacity(integer(hud, "opacity"));
                if (!imported && json.has("serverProfiles")) {
                    var mappings = json.getAsJsonObject("serverProfiles");
                    if (mappings.size() > 256) throw new JsonParseException("Too many server assignments");
                    for (var entry : mappings.entrySet()) {
                        String key = serverKey(entry.getKey()), id = entry.getValue().getAsString();
                        if (key == null || !ids.contains(id)) throw new JsonParseException("Invalid server assignment");
                        config.servers.put(key, id);
                    }
                }
            }
            if (imported) config.setOption(Option.AUTO_SERVER, false);
            return config;
        } catch (RuntimeException e) { throw new IOException("Invalid NoMiss settings: " + e.getMessage(), e); }
    }
    private static String string(JsonObject object, String key) {
        var value = object.get(key); if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected text: " + key);
        return value.getAsString();
    }
    private static boolean bool(JsonObject object, String key) {
        var value = object.get(key); if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new JsonParseException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
    private static int integer(JsonObject object, String key) {
        var value = object.get(key); if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("[0-9]+")) throw new JsonParseException("Expected integer: " + key);
        return value.getAsInt();
    }
    public String toJson(boolean export) {
        JsonObject json = new JsonObject(); json.addProperty("schemaVersion", 2);
        for (Option option : Option.values()) if (option != Option.COOLDOWN && option != Option.BLOCKS) json.addProperty(option.jsonKey, option == Option.AUTO_SERVER && export ? false : option(option));
        // Save the manual profile while connected, so restarting elsewhere never inherits a server override.
        json.addProperty("activeProfile", !export && serverScoped && returnProfile != null ? returnProfile : activeId);
        var list = new JsonArray();
        for (var profile : profiles) {
            var p = new JsonObject(); p.addProperty("id", profile.id()); p.addProperty("name", profile.name());
            p.addProperty("cooldown", profile.cooldown()); p.addProperty("blocks", profile.blocks());
            p.add("slots", GSON.toJsonTree(profile.slots())); list.add(p);
        }
        json.add("profiles", list);
        var h = new JsonObject(); h.addProperty("corner", hud.corner().name()); h.addProperty("scale", hud.scale()); h.addProperty("opacity", hud.opacity()); json.add("hud", h);
        if (!export) json.add("serverProfiles", GSON.toJsonTree(servers));
        return GSON.toJson(json) + System.lineSeparator();
    }
    public void save(Path path) throws IOException {
        String text = toJson(false);
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_LENGTH) throw new IOException("NoMiss settings exceed the supported size; the previous file was kept");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "nomiss-", ".tmp");
        try {
            Files.writeString(temp, text);
            try { Files.move(temp, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
