package com.nomiss.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.nomiss.config.AttackProtection.Target.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackProtectionTest {
    @Test void normalModeBlocksMissesButAllowsLowStrengthHitsAndBlocks() {
        var config = new NoMissConfig();
        assertTrue(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", MISS, 1));
        assertTrue(AttackProtection.shouldCancel(config, "minecraft:mace", MISS, 0));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:diamond_spear", ENTITY, 0.2F));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", BLOCK, 0));
    }
    @Test void strictModeBlocksBlocksWithoutChangingValidHits() {
        var config = new NoMissConfig(); config.setAllowBlockBreaking(false);
        assertTrue(AttackProtection.shouldCancel(config, "minecraft:mace", BLOCK, 1));
        assertTrue(AttackProtection.shouldCancel(config, "minecraft:mace", MISS, 1));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:mace", ENTITY, 0));
    }
    @Test void cooldownModeRejectsEarlyHitsWithoutQueuingAndStillAllowsBlockBreaking() {
        var config = new NoMissConfig(); config.setBlockDuringCooldown(true);
        for (float charge : new float[]{0, 0.5F, 0.99F})
            assertTrue(AttackProtection.shouldCancel(config, "minecraft:iron_sword", ENTITY, charge));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:iron_sword", ENTITY, 1));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:iron_sword", BLOCK, 0));
    }
    @ParameterizedTest @EnumSource(AttackProtection.Target.class)
    void disabledAndUnselectedItemsKeepVanillaBehavior(AttackProtection.Target target) {
        var config = new NoMissConfig(); config.setBlockDuringCooldown(true); config.setAllowBlockBreaking(false);
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:diamond_axe", target, 0));
        config.setEnabled(false);
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:mace", target, 0));
    }
    @Test void customSelectionTakesEffectImmediately() {
        var config = new NoMissConfig(); config.clearItems(); config.setItem(19, "example:weapon");
        assertTrue(AttackProtection.shouldCancel(config, "example:weapon", MISS, 1));
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:mace", MISS, 1));
        config.setItem(19, "");
        assertFalse(AttackProtection.shouldCancel(config, "example:weapon", MISS, 1));
    }
}
