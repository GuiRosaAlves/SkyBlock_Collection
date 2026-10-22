package io.github.guirosaalves.skyvoid;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidVoidStructureSelectorTest {
    private static SkyvoidVoidStructureSelector.Context snowy(long seed) {
        return new SkyvoidVoidStructureSelector.Context(
                seed, "minecraft:overworld", 18, -33,
                "minecraft:snowy_plains", "minecraft:village_snowy");
    }

    private static SkyvoidVoidStructureSelector.Candidate allowed(String id, int weight) {
        return new SkyvoidVoidStructureSelector.Candidate(id, weight, context -> true);
    }

    @Test void sameSeedAndChunkGiveSameOrderingRegardlessOfRegistryOrder() {
        var entries = List.of(allowed("minecraft:igloo", 5),
                              allowed("minecraft:village_snowy", 2),
                              allowed("custom:snow_cabin", 8));
        var reordered = List.of(entries.get(2), entries.get(0), entries.get(1));
        var context = snowy(81818181L);
        assertEquals(SkyvoidVoidStructureSelector.ranked(context, entries),
                     SkyvoidVoidStructureSelector.ranked(context, reordered));
        assertEquals(SkyvoidVoidStructureSelector.selectionSeed(context),
                     SkyvoidVoidStructureSelector.selectionSeed(snowy(81818181L)));
        assertNotEquals(SkyvoidVoidStructureSelector.selectionSeed(context),
                        SkyvoidVoidStructureSelector.selectionSeed(snowy(81818182L)));
    }

    @Test void unavailableBiomeAndDisabledStructuresAreNeverChosen() {
        var entries = List.of(
                allowed("minecraft:igloo", 2),
                allowed("minecraft:village_snowy", 0),
                new SkyvoidVoidStructureSelector.Candidate("minecraft:desert_pyramid", 1000,
                    c -> "minecraft:desert".equals(c.biomeId())),
                new SkyvoidVoidStructureSelector.Candidate("custom:nether_castle", 1000,
                    c -> "minecraft:the_nether".equals(c.dimension())));
        assertEquals(List.of("minecraft:igloo"),
                SkyvoidVoidStructureSelector.ranked(snowy(100L), entries)
                        .stream().map(SkyvoidVoidStructureSelector.Candidate::id).toList());
    }

    @Test void triesNextEligibleCandidateIfFirstStartCannotGenerate() {
        var candidates = List.of(allowed("modded:snow_tower", 100),
                                 allowed("minecraft:igloo", 1));
        var order = SkyvoidVoidStructureSelector.ranked(snowy(3L), candidates);
        var winner = SkyvoidVoidStructureSelector.firstValid(snowy(3L), candidates,
                c -> !c.id().equals(order.getFirst().id()));
        assertEquals(order.getLast().id(), winner.orElseThrow().id());
    }

    @Test void noEligibleStructuresMeansNoSelection() {
        var entries = List.of(allowed("minecraft:igloo", 0));
        assertTrue(SkyvoidVoidStructureSelector.ranked(snowy(1L), entries).isEmpty());
        assertEquals(Optional.empty(),
                SkyvoidVoidStructureSelector.firstValid(snowy(1L), entries, c -> true));
    }

    @Test void weightedSamplingFavorsHigherWeightAcrossManyWorldSeeds() {
        var entries = List.of(allowed("minecraft:igloo", 1),
                              allowed("modded:snow_tower", 9));
        int expensive = 0;
        for (long seed = 0; seed < 2000; seed++) {
            if (SkyvoidVoidStructureSelector.ranked(snowy(seed), entries)
                    .getFirst().id().equals("modded:snow_tower")) {
                expensive++;
            }
        }
        assertTrue(expensive > 1650 && expensive < 1950,
                "Weight 9 should win about 90% of candidate rolls: " + expensive);
    }

    @Test void rejectsDuplicateIdsAndInvalidWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyvoidVoidStructureSelector.ranked(snowy(5L),
                        List.of(allowed("minecraft:igloo", 1), allowed("minecraft:igloo", 2))));
        assertThrows(IllegalArgumentException.class,
                () -> allowed("minecraft:igloo", -1));
        assertThrows(IllegalArgumentException.class,
                () -> allowed("minecraft:igloo", 1001));
        assertThrows(IllegalArgumentException.class,
                () -> allowed("not-a-namespace", 1));
    }
}
