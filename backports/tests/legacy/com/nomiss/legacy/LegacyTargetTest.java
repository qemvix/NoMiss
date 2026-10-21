package com.nomiss.legacy;

import com.nomiss.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTargetTest {
    @Test void oldTileTargetStillAllowsMiningByDefault() {
        var config = new NoMissConfig();
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", LegacyTarget.fromName("TILE"), 1F));
    }
    @Test void tileAndBlockRespectDisabledMining() {
        var config = new NoMissConfig(); config.setAllowBlockBreaking(false);
        for (String target : new String[]{"TILE", "BLOCK"})
            assertTrue(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", LegacyTarget.fromName(target), 1F));
    }
    @Test void oldEntityAndMissTargetsAreDistinct() {
        var config = new NoMissConfig();
        assertFalse(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", LegacyTarget.fromName("ENTITY"), 1F));
        assertTrue(AttackProtection.shouldCancel(config, "minecraft:diamond_sword", LegacyTarget.fromName("MISS"), 1F));
    }
}
