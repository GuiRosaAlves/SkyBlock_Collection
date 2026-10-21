package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidWorldProfileTest {
    @Test void uninitializedProfilesDoNotSilentlyBecomeNewWorldProfiles() {
        var profile = new SkyvoidWorldProfile();
        assertFalse(profile.initialized());
        assertThrows(IllegalStateException.class, () -> profile.changeAdditions(Map.of("foo", true)));
    }

    @Test void independentProfilesPreserveOwnSettings() {
        var a = new SkyvoidWorldProfile();
        var b = new SkyvoidWorldProfile();
        a.initialize("mod_new_world", "skyvoid_worldgen", "skyvoid_island_standard", Map.of(), Map.of("cobwebs_from_spiders", false), true);
        b.initialize("mod_new_world", "skyvoid_worldgen_empty", "skyvoid_island_oneblock", Map.of(), Map.of("cobwebs_from_spiders", true), true);
        a.changeAdditions(Map.of("cobwebs_from_spiders", true));
        assertEquals("skyvoid_worldgen", a.worldgen());
        assertEquals("skyvoid_worldgen_empty", b.worldgen());
        assertTrue(b.additions().get("cobwebs_from_spiders"));
        assertThrows(IllegalStateException.class, () -> a.initialize("legacy_datapack", "x", "y", Map.of(), Map.of(), true));
    }

    @Test void codecRoundTripPreservesMetadataAndGameplayValues() {
        var source = new SkyvoidWorldProfile();
        source.initialize("legacy_datapack", "skyvoid_worldgen", "skyvoid_island_standard",
                Map.of("skyvoid_additions", true), Map.of("sniffer_eggs_from_traders", false), true);
        JsonElement json = SkyvoidWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        var restored = SkyvoidWorldProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(restored.migrationComplete());
        assertEquals("legacy_datapack", restored.origin());
        assertEquals(source.worldgen(), restored.worldgen());
        assertEquals(source.island(), restored.island());
        assertEquals(source.optionalPacks(), restored.optionalPacks());
        assertEquals(source.additions(), restored.additions());
    }
}
