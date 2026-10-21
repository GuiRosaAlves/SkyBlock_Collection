package io.github.guirosaalves.skyvoid;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidLegacyPackDetectorTest {
    @Test void recognizesOriginalStandaloneZips() {
        assertTrue(SkyvoidLegacyPackDetector.hasExternalAdditions(
                List.of("vanilla", "file/skyvoid_additions_v1_6_0-MC_26_2.zip")));
    }

    @Test void ignoresEmbeddedModPackAndUnrelatedDatapacks() {
        assertFalse(SkyvoidLegacyPackDetector.hasExternalAdditions(
                List.of("vanilla", "mod/skyvoid_additions", "file/skyvoid_worldgen.zip")));
    }
}
