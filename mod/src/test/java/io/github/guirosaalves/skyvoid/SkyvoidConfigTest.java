package io.github.guirosaalves.skyvoid;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class SkyvoidConfigTest {
    @Test
    void missingWorldSelectionValuesAreReplacedWithDefaults() {
        // NeoForge corrects a blank TOML on the first launch, so it passes
        // null to each validator for a missing entry. This used to crash in
        // List.of(...).contains(null).
        var config = CommentedConfig.inMemory();
        assertDoesNotThrow(() -> SkyvoidConfig.SPEC.correct(config));
        assertEquals(SkyvoidConfig.WORLDGEN_IDS.getFirst(),
                config.get("new_world.worldgen"));
        assertEquals(SkyvoidConfig.ISLAND_IDS.getFirst(),
                config.get("new_world.island"));
    }
}
