package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidStructurePackTest {
    private static JsonObject baseline() {
        return JsonParser.parseString("""
                {"structures":[
                 {"structure":"skyvoid_biome_islands:badlands","weight":1},
                 {"structure":"skyvoid_biome_islands:desert","weight":1}],
                 "placement":{"type":"minecraft:random_spread",
                 "spacing":20,"separation":8,"salt":10387312,
                 "spread_type":"triangular"}}
                """).getAsJsonObject();
    }

    @Test void normalSettingsDoNotChangeOriginalDatapack() {
        JsonObject original = baseline();
        assertEquals(original, SkyvoidStructurePack.transform(original, Map.of(), Map.of(), "normal"));
    }

    @Test void disableWeightAndRarityPreserveUnrelatedPlacementFields() {
        var changed = SkyvoidStructurePack.transform(baseline(),
                Map.of("badlands", false), Map.of("desert", 4), "rare");
        assertEquals(1, changed.getAsJsonArray("structures").size());
        assertEquals("skyvoid_biome_islands:desert",
                changed.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString());
        assertEquals(4, changed.getAsJsonArray("structures").get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals(30, changed.getAsJsonObject("placement").get("spacing").getAsInt());
        assertEquals(10387312, changed.getAsJsonObject("placement").get("salt").getAsInt());
        assertEquals("triangular", changed.getAsJsonObject("placement").get("spread_type").getAsString());
    }

    @Test void disablingAllCreatesValidEmptyProbabilitySet() {
        var changed = SkyvoidStructurePack.transform(baseline(),
                Map.of("badlands", false, "desert", false), Map.of(), "normal");
        assertEquals(1, changed.getAsJsonArray("structures").size());
        assertEquals(0.0, changed.getAsJsonObject("placement").get("frequency").getAsDouble());
    }

    @Test void densityNeverMakesSeparationInvalid() {
        var changed = SkyvoidStructurePack.transform(baseline(), Map.of(), Map.of(), "very_common");
        assertTrue(changed.getAsJsonObject("placement").get("spacing").getAsInt() >
                   changed.getAsJsonObject("placement").get("separation").getAsInt());
    }

    @Test void rejectsUnknownDensityAndInvalidWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyvoidStructurePack.transform(baseline(), Map.of(), Map.of(), "unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> SkyvoidStructurePack.transform(baseline(), Map.of(), Map.of("desert", 0), "normal"));
    }
}
