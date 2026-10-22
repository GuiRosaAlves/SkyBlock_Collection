package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidStructureSetRuleTest {
    @Test void savedStructureSettingsRoundTripAndDoNotAffectOtherSaves() {
        var worldA = new SkyvoidWorldProfile();
        var worldB = new SkyvoidWorldProfile();
        worldA.initialize("mod_new_world", "skyvoid_worldgen", "skyvoid_island_standard",
                Map.of(), Map.of(), true);
        worldB.initialize("mod_new_world", "skyvoid_worldgen", "skyvoid_island_standard",
                Map.of(), Map.of(), true);
        var setting = new SkyvoidStructureSetRule("rare",
                Map.of("skyvoid_biome_islands:badlands", false),
                Map.of("skyvoid_biome_islands:desert", 4));
        worldA.changeStructureSets(Map.of("skyvoid_biome_islands:biome_islands", setting));
        JsonElement saved = SkyvoidWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, worldA).getOrThrow();
        var loaded = SkyvoidWorldProfile.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(worldA.structureSets(), loaded.structureSets());
        assertEquals(Map.of(), worldB.structureSets());
        assertThrows(IllegalStateException.class, () -> new SkyvoidWorldProfile()
                .changeStructureSets(Map.of("test", setting)));
    }

    @Test void invalidDensityAndWeightsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new SkyvoidStructureSetRule("never", Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new SkyvoidStructureSetRule("normal", Map.of(), Map.of("test", 0)));
    }

    @Test void olderWorldProfilesWithoutStructuresStillLoad() {
        var original = new SkyvoidWorldProfile();
        original.initialize("mod_new_world", "skyvoid_worldgen", "skyvoid_island_standard",
                Map.of(), Map.of(), true);
        JsonElement json = SkyvoidWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        json.getAsJsonObject().remove("structure_sets");
        var legacy = SkyvoidWorldProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(legacy.structureSets().isEmpty());
    }
}
