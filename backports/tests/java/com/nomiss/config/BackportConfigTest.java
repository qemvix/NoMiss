package com.nomiss.config;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackportConfigTest {
    private List<String> expected() { return List.of(System.getProperty("nomiss.categories").split(",")); }
    private void assertWeapons(NoMissProfile profile) {
        for (int i = 0; i < expected().size(); i++) assertEquals(WeaponSelection.category(expected().get(i)), profile.slot(i));
        assertTrue(profile.slots().stream().filter(s -> !s.isEmpty())
            .allMatch(s -> expected().contains(s.value()) || s.value().equals("axes")));
    }
    @Test void presetsUseExistingWeaponsAndNeverEnableCooldown() {
        var config = new NoMissConfig();
        for (var profile : config.profiles()) { assertFalse(profile.cooldown()); assertWeapons(profile); }
        assertFalse(config.profiles().get(1).blocks());
        assertEquals(WeaponSelection.category("axes"), config.profiles().get(2).slot(expected().size()));
    }
    @Test void newProfilesAndResetUseVersionDefaults() {
        var config = new NoMissConfig();
        var profile = config.addProfile("Test", false);
        assertFalse(profile.cooldown()); assertWeapons(profile);
        config.activateProfile(profile.id()); config.clearItems(); config.resetItems(); assertWeapons(profile);
    }
    @Test void exactRulesTakePriorityAndManualSettingsSurviveRoundTrip() throws Exception {
        var config = new NoMissConfig(); config.clearItems();
        config.setSelection(0, WeaponSelection.category("swords"));
        config.setSelection(1, WeaponSelection.item("minecraft:diamond_sword").withRules(WeaponSelection.Override.OFF, WeaponSelection.Override.OFF));
        config.setSelection(2, WeaponSelection.exact("minecraft:diamond_sword", "a".repeat(64), "Named sword")
            .withRules(WeaponSelection.Override.ON, WeaponSelection.Override.ON));
        var restored = NoMissConfig.parse(config.toJson(true), true);
        var exact = restored.resolve("minecraft:diamond_sword", Set.of("swords"), "a".repeat(64));
        assertEquals(2, exact.slot()); assertTrue(exact.cooldown()); assertTrue(exact.blocks());
        assertFalse(restored.resolve("minecraft:diamond_sword", Set.of("swords"), "b".repeat(64)).blocks());
        assertFalse(restored.blockDuringCooldown());
    }
    @Test void duplicatePreservesManualCooldownButNewProfileDoesNot() {
        var config = new NoMissConfig(); config.setBlockDuringCooldown(true);
        assertTrue(config.addProfile("Copy", true).cooldown());
        assertFalse(config.addProfile("New", false).cooldown());
    }
}
