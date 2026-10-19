package com.nomiss.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NoMissConfigTest {
    @TempDir Path directory;

    @Test void defaultsMatchRequestedWeaponsAndBehavior() {
        var config = new NoMissConfig();
        assertTrue(config.enabled());
        assertTrue(config.allowBlockBreaking());
        assertFalse(config.blockDuringCooldown());
        assertEquals(15, config.items().size());
        assertTrue(config.contains("minecraft:diamond_sword"));
        assertTrue(config.contains("minecraft:copper_spear"));
        assertTrue(config.contains("minecraft:mace"));
        assertFalse(config.contains("minecraft:diamond_axe"));
    }

    @Test void allTwentySlotsCanBeFilledAndReplacedWithoutDuplicates() {
        var config = new NoMissConfig();
        config.clearItems();
        for (int i = 0; i < 20; i++) assertTrue(config.setItem(i, "example:item_" + i));
        assertEquals(20, config.items().size());
        assertFalse(config.setItem(20, "example:extra"));
        assertFalse(config.setItem(-1, "example:extra"));
        assertFalse(config.setItem(5, "example:item_3"));
        assertTrue(config.setItem(5, "example:replacement"));
        assertFalse(config.contains("example:item_5"));
        assertTrue(config.contains("example:replacement"));
        assertTrue(config.setItem(3, ""));
        assertTrue(config.setItem(5, "example:item_3"));
        assertEquals(19, config.items().size());
    }

    @Test void savesSettingsAndEmptySlotsAcrossRestarts() throws IOException {
        var config = new NoMissConfig();
        config.setEnabled(false); config.setAllowBlockBreaking(false); config.setBlockDuringCooldown(true);
        config.setItem(0, ""); config.setItem(19, "custom:very_long/path");
        Path path = directory.resolve("config/nomiss.json");
        config.save(path);
        var loaded = NoMissConfig.load(path);
        assertFalse(loaded.enabled()); assertFalse(loaded.allowBlockBreaking()); assertTrue(loaded.blockDuringCooldown());
        assertEquals("", loaded.itemAt(0));
        assertEquals("custom:very_long/path", loaded.itemAt(19));
        assertEquals(config.items(), loaded.items());
        config.clearItems(); config.save(path);
        assertTrue(NoMissConfig.load(path).items().isEmpty());
        try (var files = Files.list(path.getParent())) { assertEquals(1, files.count()); }
    }

    @Test void legacyDisableSentinelIsMigratedOnlyBeforeJsonExists() throws IOException {
        Files.createFile(directory.resolve("nomiss.off"));
        Path path = directory.resolve("nomiss.json");
        var config = NoMissConfig.load(path);
        assertFalse(config.enabled());
        config.setEnabled(true); config.save(path);
        assertTrue(NoMissConfig.load(path).enabled());
    }

    @Test void malformedConfigIsReportedAndPreserved() throws IOException {
        Path path = directory.resolve("nomiss.json");
        for (String text : new String[]{"{broken", "null", "[]", "{\"items\":null}", "{\"enabled\":\"maybe\"}"}) {
            Files.writeString(path, text);
            assertThrows(IOException.class, () -> NoMissConfig.load(path), text);
            assertEquals(text, Files.readString(path));
        }
    }

    @Test void editedConfigCannotExceedLimitOrSelectInvalidAndDuplicateIds() throws IOException {
        Path path = directory.resolve("nomiss.json");
        Files.writeString(path, "{\"items\":[\"minecraft:mace\",\"minecraft:mace\",\"INVALID\",null,\"unknown:valid_item\"]}");
        var config = NoMissConfig.load(path);
        assertEquals(2, config.items().size());
        assertEquals("", config.itemAt(1));
        assertEquals("", config.itemAt(2));
        assertEquals("unknown:valid_item", config.itemAt(4));
        assertFalse(config.setItem(8, "bad id"));
        assertFalse(config.setItem(8, null));
        String items = java.util.stream.IntStream.range(0, 30).mapToObj(i -> "\"custom:item_" + i + "\"")
            .collect(java.util.stream.Collectors.joining(","));
        Files.writeString(path, "{\"items\":[" + items + "]}");
        assertEquals(20, NoMissConfig.load(path).items().size());
    }

    @Test void defaultsCanBeRestoredWithoutResettingOtherSettings() {
        var config = new NoMissConfig();
        config.setEnabled(false); config.setBlockDuringCooldown(true); config.clearItems(); config.resetItems();
        assertFalse(config.enabled()); assertTrue(config.blockDuringCooldown());
        assertEquals(new NoMissConfig().items(), config.items());
    }
}
