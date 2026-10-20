package com.nomiss.config;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.nomiss.config.WeaponSelection.Override.*;

class ProfilesTest {
    @TempDir Path directory;
    private static final String SIGNATURE = "a".repeat(64);
    @Test void allPresetsStartWithCooldownOffAndCategoriesUseOneSlot() {
        var config = new NoMissConfig();
        assertEquals(3, config.profiles().size());
        for (var profile : config.profiles()) assertFalse(profile.cooldown());
        assertTrue(config.activeProfile().blocks());
        assertTrue(config.activateProfile("pvp")); assertFalse(config.allowBlockBreaking()); assertFalse(config.blockDuringCooldown());
        assertEquals(3, config.items().size());
        assertNotNull(config.resolve("example:ruby_blade", Set.of("swords"), ""));
        assertNull(config.resolve("example:unclassified_sword", Set.of(), ""));
        assertTrue(config.activateProfile("modpack")); assertEquals(4, config.items().size());
        for (int i = 0; i < 500; i++) assertNotNull(config.resolve("mod:item_" + i, Set.of("swords"), ""));
        assertEquals(4, config.items().size());
    }
    @Test void migrationKeepsOldSlotsAndManuallyEnabledCooldown() throws IOException {
        var config = NoMissConfig.parse("{\"enabled\":false,\"blockDuringCooldown\":true,\"allowBlockBreaking\":false,\"showStatus\":false,\"showBlockedAttacks\":true,\"items\":[\"minecraft:diamond_sword\",\"\",\"missing:weapon\"]}", false);
        assertFalse(config.enabled()); assertTrue(config.blockDuringCooldown()); assertFalse(config.allowBlockBreaking());
        assertFalse(config.showStatus()); assertTrue(config.showBlockedAttacks());
        assertEquals("minecraft:diamond_sword", config.itemAt(0)); assertEquals("", config.itemAt(1)); assertEquals("missing:weapon", config.itemAt(2));
        var loaded = NoMissConfig.parse(config.toJson(false), false);
        assertEquals(config.items(), loaded.items()); assertTrue(loaded.blockDuringCooldown()); assertFalse(loaded.option(NoMissConfig.Option.AUTO_SERVER));
        assertTrue(loaded.profiles().stream().filter(p -> !p.id().equals("survival")).noneMatch(NoMissProfile::cooldown));
    }
    @Test void exactThenIndividualThenCategoryPrioritySurvivesRoundTrip() throws IOException {
        var config = new NoMissConfig();
        config.setSelection(5, WeaponSelection.item("mod:blade").withRules(ON, OFF));
        config.setSelection(9, WeaponSelection.exact("mod:blade", SIGNATURE, "My sword").withRules(OFF, ON));
        config = NoMissConfig.parse(config.toJson(false), false);
        var exact = config.resolve("mod:blade", Set.of("swords"), SIGNATURE);
        assertEquals(9, exact.slot()); assertFalse(exact.cooldown()); assertTrue(exact.blocks());
        var type = config.resolve("mod:blade", Set.of("swords"), "b".repeat(64));
        assertEquals(5, type.slot()); assertTrue(type.cooldown()); assertFalse(type.blocks());
        var category = config.resolve("other:blade", Set.of("swords"), SIGNATURE);
        assertEquals(0, category.slot()); assertFalse(category.cooldown()); assertTrue(category.blocks());
    }
    @Test void inheritedAndExplicitRulesReactDifferentlyToProfileDefaults() {
        var config = new NoMissConfig();
        config.setSelection(1, config.selection(1).withRules(OFF, ON));
        config.setBlockDuringCooldown(true); config.setAllowBlockBreaking(false);
        var sword = config.resolve("minecraft:diamond_sword", Set.of("swords"), "");
        var spear = config.resolve("minecraft:diamond_spear", Set.of("spears"), "");
        assertTrue(sword.cooldown()); assertFalse(sword.blocks()); assertFalse(spear.cooldown()); assertTrue(spear.blocks());
        assertEquals(AttackProtection.BlockReason.COOLDOWN, AttackProtection.blockedReason(true, sword, AttackProtection.Target.ENTITY, .5F));
        assertNull(AttackProtection.blockedReason(true, spear, AttackProtection.Target.ENTITY, .5F));
        assertNull(AttackProtection.blockedReason(true, spear, AttackProtection.Target.BLOCK, 0));
        config.setSelection(1, config.selection(1).withRules(INHERIT, INHERIT));
        assertTrue(config.resolve("mod:spear", Set.of("spears"), "").cooldown());
    }
    @Test void duplicateProfilesAreIndependentAndNewProfilesDoNotInheritCooldown() {
        var config = new NoMissConfig(); config.setBlockDuringCooldown(true);
        config.setSelection(0, config.selection(0).withRules(ON, OFF));
        var copy = config.addProfile("My copy", true);
        assertTrue(copy.cooldown()); assertEquals(ON, copy.slot(0).cooldown());
        config.setSelection(0, WeaponSelection.empty()); assertFalse(copy.slot(0).isEmpty());
        var fresh = config.addProfile("New", false); assertFalse(fresh.cooldown());
        copy.rename("Renamed"); assertNotEquals(copy.name(), config.activeProfile().name());
        assertFalse(config.activateProfile("missing")); assertEquals("survival", config.activeProfile().id());
    }
    @Test void exactAndCategorySelectionsRejectDuplicatesButAllowIntentionalOverrides() {
        var config = new NoMissConfig();
        assertFalse(config.setSelection(4, WeaponSelection.category("swords")));
        assertTrue(config.setSelection(4, WeaponSelection.item("minecraft:diamond_sword")));
        assertTrue(config.setSelection(5, WeaponSelection.exact("minecraft:diamond_sword", SIGNATURE, "named")));
        assertFalse(config.setSelection(6, WeaponSelection.exact("minecraft:diamond_sword", SIGNATURE, "other label")));
        assertTrue(config.setSelection(6, WeaponSelection.exact("minecraft:diamond_sword", "b".repeat(64), "another sword")));
        assertThrows(IllegalArgumentException.class, () -> WeaponSelection.category("all_items"));
        assertThrows(IllegalArgumentException.class, () -> WeaponSelection.exact("minecraft:diamond_sword", "invalid", "name"));
    }
    @Test void categoryNamesCannotCollideWithValidModdedItemIds() {
        var config = new NoMissConfig();
        assertTrue(config.setSelection(7, WeaponSelection.item("category:swords")));
        assertEquals(4, config.items().size());
        assertEquals(7, config.resolve("category:swords", Set.of(), "").slot());
        assertEquals(0, config.resolve("mod:blade", Set.of("swords"), "").slot());
    }
    @Test void automaticProfilesRememberPerServerAndRestoreManualSelection() throws IOException {
        var config = new NoMissConfig(); config.setOption(NoMissConfig.Option.AUTO_SERVER, true);
        config.connectServer("PLAY.Example.COM:25565"); config.activateProfile("pvp");
        assertEquals("pvp", config.serverProfiles().get("play.example.com"));
        assertEquals("pvp", config.activeProfile().id()); assertFalse(config.blockDuringCooldown());
        var reloaded = NoMissConfig.parse(config.toJson(false), false);
        assertEquals("survival", reloaded.activeProfile().id());
        config.connectServer("play.example.com"); assertEquals("pvp", config.activeProfile().id());
        config.disconnectServer(); assertEquals("survival", config.activeProfile().id());
        config.connectServer("another.example.com"); config.activateProfile("modpack"); config.disconnectServer();
        config.connectServer("play.example.com"); assertEquals("pvp", config.activeProfile().id());
        config.disconnectServer(); assertEquals("survival", config.activeProfile().id());
    }
    @Test void assignmentsAreInactiveUntilEnabledAndCanBeEnabledWhileConnected() {
        var config = new NoMissConfig(); config.connectServer("host:25566"); config.activateProfile("pvp"); config.rememberServer(); config.disconnectServer();
        config.activateProfile("survival"); config.connectServer("host:25566"); assertEquals("survival", config.activeProfile().id());
        config.setOption(NoMissConfig.Option.AUTO_SERVER, true); assertEquals("pvp", config.activeProfile().id());
        config.disconnectServer(); assertEquals("survival", config.activeProfile().id());
        assertEquals("host:25566", NoMissConfig.serverKey("HOST:25566")); assertNull(NoMissConfig.serverKey("\n"));
    }
    @Test void disablingAutomaticProfilesKeepsTheCurrentSelectionManual() {
        var config = new NoMissConfig(); config.setOption(NoMissConfig.Option.AUTO_SERVER, true);
        config.connectServer("server"); config.activateProfile("pvp");
        config.setOption(NoMissConfig.Option.AUTO_SERVER, false); config.disconnectServer();
        assertEquals("pvp", config.activeProfile().id());
    }
    @Test void deletingProfilesCleansAssignmentsAndNeverDeletesTheLastOne() {
        var config = new NoMissConfig(); config.connectServer("server"); config.activateProfile("pvp"); config.rememberServer();
        assertTrue(config.deleteProfile("pvp")); assertTrue(config.serverProfiles().isEmpty()); assertEquals("survival", config.activeProfile().id());
        assertTrue(config.deleteProfile("modpack")); assertFalse(config.deleteProfile("survival")); assertEquals(1, config.profiles().size());
    }
    @Test void exportsExcludeServerAddressesAndImportsDisableAutomaticSwitching() throws IOException {
        var config = new NoMissConfig(); config.setOption(NoMissConfig.Option.AUTO_SERVER, true); config.connectServer("private.example.org"); config.activateProfile("pvp");
        config.hud().setCorner(HudSettings.Corner.BOTTOM_RIGHT); config.hud().setScale(150); config.hud().setOpacity(40);
        String exported = config.toJson(true); assertFalse(exported.contains("private.example.org")); assertFalse(exported.contains("serverProfiles"));
        var imported = NoMissConfig.parse(exported, true); assertFalse(imported.option(NoMissConfig.Option.AUTO_SERVER)); assertTrue(imported.serverProfiles().isEmpty());
        assertEquals("pvp", imported.activeProfile().id()); assertEquals(HudSettings.Corner.BOTTOM_RIGHT, imported.hud().corner()); assertEquals(150, imported.hud().scale()); assertEquals(40, imported.hud().opacity());
        var importedLocal = NoMissConfig.parse(config.toJson(false), true); assertTrue(importedLocal.serverProfiles().isEmpty()); assertFalse(importedLocal.option(NoMissConfig.Option.AUTO_SERVER));
    }
    @Test void invalidImportsAndFutureFormatsAreRejectedWithoutWriting() throws IOException {
        var config = new NoMissConfig(); Path file = directory.resolve("nomiss.json"); config.save(file); String before = Files.readString(file);
        for (String bad : List.of("{}bad", "null", "[]", "{\"schemaVersion\":3}", "{\"schemaVersion\":2.5}", " ".repeat(NoMissConfig.MAX_JSON_LENGTH + 1)))
            assertThrows(IOException.class, () -> NoMissConfig.parse(bad, true));
        var json = JsonParser.parseString(before).getAsJsonObject(); json.addProperty("activeProfile", "unknown");
        assertThrows(IOException.class, () -> NoMissConfig.parse(json.toString(), true));
        assertEquals(before, Files.readString(file));
    }
    @Test void invalidNewSchemasCannotSilentlyDropProfilesOrSlots() throws IOException {
        String original = new NoMissConfig().toJson(false);
        for (String which : List.of("emptyProfiles", "duplicateProfiles", "missingSlots", "unknownCategory", "badBoolean", "badHud")) {
            var json = JsonParser.parseString(original).getAsJsonObject(); var profiles = json.getAsJsonArray("profiles");
            switch (which) {
                case "emptyProfiles" -> profiles.remove(0); // clear remaining entries below
                case "duplicateProfiles" -> profiles.add(profiles.get(0).deepCopy());
                case "missingSlots" -> profiles.get(0).getAsJsonObject().getAsJsonArray("slots").remove(0);
                case "unknownCategory" -> profiles.get(0).getAsJsonObject().getAsJsonArray("slots").get(0).getAsJsonObject().addProperty("value", "everything");
                case "badBoolean" -> profiles.get(0).getAsJsonObject().addProperty("cooldown", "yes");
                case "badHud" -> json.getAsJsonObject("hud").addProperty("opacity", 0);
            }
            if (which.equals("emptyProfiles")) { profiles.remove(0); profiles.remove(0); }
            assertThrows(IOException.class, () -> NoMissConfig.parse(json.toString(), true), which);
        }
    }
    @Test void oversizedSettingsCannotReplaceTheLastLoadableConfiguration() throws IOException {
        var config = new NoMissConfig(); Path file = directory.resolve("nomiss.json"); config.save(file);
        String before = Files.readString(file);
        while (config.profiles().size() < NoMissConfig.MAX_PROFILES) config.addProfile("Another", false);
        for (var profile : config.profiles()) for (int i = 0; i < NoMissConfig.MAX_ITEMS; i++)
            profile.setSlot(i, WeaponSelection.item("custom:" + "x".repeat(1024) + i));
        assertThrows(IOException.class, () -> config.save(file)); assertEquals(before, Files.readString(file));
        assertThrows(IOException.class, () -> NoMissConfig.parse("界".repeat(NoMissConfig.MAX_JSON_LENGTH / 2), true));
    }
    @Test void profileAndHudBoundsRejectInvalidChangesWithoutMutatingData() {
        var config = new NoMissConfig();
        assertThrows(IllegalArgumentException.class, () -> config.addProfile(" ", false)); assertEquals(3, config.profiles().size());
        while (config.profiles().size() < NoMissConfig.MAX_PROFILES) config.addProfile("Another", false);
        assertThrows(IllegalArgumentException.class, () -> config.addProfile("Overflow", false));
        assertThrows(IllegalArgumentException.class, () -> config.hud().setScale(0)); assertEquals(100, config.hud().scale());
        assertThrows(IllegalArgumentException.class, () -> config.hud().setOpacity(101)); assertEquals(100, config.hud().opacity());
    }
}
