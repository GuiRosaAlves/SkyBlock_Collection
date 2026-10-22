package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidVoidStructureWorldProfileTest {
    private static SkyvoidWorldProfile profile(String name) {
        var profile = new SkyvoidWorldProfile();
        profile.initialize("mod_new_world", "skyvoid_worldgen_structures", name,
                           Map.of(), Map.of(), true);
        return profile;
    }

    @Test void preservesDisabledAndWeightedModdedStructuresPerWorld() {
        var a = profile("skyvoid_island_standard");
        var b = profile("skyvoid_island_oneblock");
        a.changeVoidStructureWeights(Map.of("minecraft:igloo", 0, "custom:snow_tower", 7));
        JsonElement json = SkyvoidWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        var loaded = SkyvoidWorldProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(0, loaded.voidStructureWeights().get("minecraft:igloo"));
        assertEquals(7, loaded.voidStructureWeights().get("custom:snow_tower"));
        assertTrue(b.voidStructureWeights().isEmpty());
    }

    @Test void oldSchemaProfilesStillLoadAsNoOverrides() {
        var source = profile("skyvoid_island_standard");
        var json = SkyvoidWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        json.getAsJsonObject().remove("void_structure_weights");
        var restored = SkyvoidWorldProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(restored.voidStructureWeights().isEmpty());
    }

    @Test void validatesIdsBoundsAndInitialization() {
        var p = profile("skyvoid_island_standard");
        assertThrows(IllegalArgumentException.class,
                () -> p.changeVoidStructureWeights(Map.of("invalid", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> p.changeVoidStructureWeights(Map.of("minecraft:igloo", -1)));
        assertThrows(IllegalArgumentException.class,
                () -> p.changeVoidStructureWeights(Map.of("minecraft:igloo", 1001)));
        assertThrows(IllegalStateException.class,
                () -> new SkyvoidWorldProfile().changeVoidStructureWeights(Map.of()));
    }
}
