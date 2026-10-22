package io.github.guirosaalves.skyvoid;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidVoidStructureRuntimeTest {
    @Test void savedWorldBeatsGlobalDefaultsEvenForEmptyWeights() {
        var oldSave = new SkyvoidWorldProfile();
        oldSave.initialize("legacy_datapack", "skyvoid_worldgen_structures",
                "skyvoid_island_standard", Map.of(), Map.of(), true);
        var resolved = SkyvoidVoidStructureRuntime.resolve(oldSave, true,
                "skyvoid_worldgen_empty", Map.of("minecraft:igloo", 10));
        assertEquals("skyvoid_worldgen_structures", resolved.worldgen());
        assertEquals(Map.of(), resolved.weights());
        oldSave.changeVoidStructureWeights(Map.of("minecraft:igloo", 0));
        assertEquals(0, SkyvoidVoidStructureRuntime.resolve(oldSave, true,
                "skyvoid_worldgen_empty", Map.of()).weights().get("minecraft:igloo"));
    }

    @Test void uninitializedSaveUsesNewWorldDefaults() {
        var fresh = new SkyvoidWorldProfile();
        var resolved = SkyvoidVoidStructureRuntime.resolve(fresh, false,
                "skyvoid_worldgen_structures", Map.of("minecraft:igloo", 5));
        assertFalse(resolved.enabled());
        assertEquals(5, resolved.weights().get("minecraft:igloo"));
    }
}
