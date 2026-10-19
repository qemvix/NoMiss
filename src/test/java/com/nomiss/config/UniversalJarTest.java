package com.nomiss.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped JAR, rather than only the Gradle dependency declarations. */
class UniversalJarTest {
    private static final List<String> MODULES = List.of("fabric-api-base", "fabric-key-mapping-api-v1",
        "fabric-lifecycle-events-v1", "fabric-resource-loader-v1");

    private Path artifact() { return Path.of(System.getProperty("nomiss.testJar")); }

    private List<JsonObject> nestedMetadata() throws IOException {
        var metadata = new ArrayList<JsonObject>();
        try (var jar = new ZipFile(artifact().toFile())) {
            var root = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("fabric.mod.json"))
                .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var nested : root.getAsJsonArray("jars")) {
                String path = nested.getAsJsonObject().get("file").getAsString();
                var entry = jar.getEntry(path);
                assertNotNull(entry, "Missing nested JAR: " + path);
                boolean found = false;
                try (var zip = new ZipInputStream(new ByteArrayInputStream(jar.getInputStream(entry).readAllBytes()))) {
                    for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                        if (e.getName().equals("fabric.mod.json")) {
                            metadata.add(JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                            found = true;
                        }
                    }
                }
                assertTrue(found, "Missing metadata in " + path);
            }
        }
        return metadata;
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.2", "26.3"})
    void shipsEveryLibraryRequiredByEachMinecraftVersion(String minecraft) throws IOException {
        var nested = nestedMetadata();
        var platforms = nested.stream().filter(m -> m.get("id").getAsString().equals("nomiss-platform")
            && m.getAsJsonObject("depends").get("minecraft").getAsString().equals(minecraft)).toList();
        assertEquals(1, platforms.size(), "Exactly one platform must match " + minecraft);
        var requirements = platforms.getFirst().getAsJsonObject("depends");
        for (String module : MODULES) {
            String version = requirements.get(module).getAsString();
            assertTrue(version.startsWith("="));
            assertEquals(1, nested.stream().filter(m -> m.get("id").getAsString().equals(module)
                && ("=" + m.get("version").getAsString()).equals(version)).count(),
                "Missing compatible " + module + " for " + minecraft);
        }
    }

    @Test
    void preservesTheSingleJarContractAndOptionalModMenu() throws IOException {
        try (var jar = new ZipFile(artifact().toFile())) {
            var root = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("fabric.mod.json"))
                .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            var versions = root.getAsJsonObject("depends").getAsJsonArray("minecraft");
            assertEquals(List.of("26.2", "26.3"), java.util.stream.StreamSupport.stream(versions.spliterator(), false)
                .map(v -> v.getAsString()).toList());
            assertTrue(root.getAsJsonObject("depends").has("nomiss-platform"));
            assertFalse(root.getAsJsonObject("depends").has("modmenu"));
            assertEquals(10, root.getAsJsonArray("jars").size());
            assertNotNull(jar.getEntry("com/nomiss/client/gui/NoMissScreen.class"));
            assertNotNull(jar.getEntry("com/nomiss/client/gui/ItemPickerScreen.class"));
        }
    }
}
