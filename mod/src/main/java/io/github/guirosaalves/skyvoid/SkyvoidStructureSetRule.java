package io.github.guirosaalves.skyvoid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * Per-structure-set rules stored with the world, not COMMON defaults.
 * These rules take effect only when the next worldgen-registry load includes
 * the generated structure overlay. Merely saving them is not a hot reload.
 */
public record SkyvoidStructureSetRule(
        String density,
        Map<String, Boolean> enabled,
        Map<String, Integer> weights
) {
    public static final Codec<SkyvoidStructureSetRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("density", "normal").forGetter(SkyvoidStructureSetRule::density),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("enabled", Map.of())
                    .forGetter(SkyvoidStructureSetRule::enabled),
            Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 1000)).optionalFieldOf("weights", Map.of())
                    .forGetter(SkyvoidStructureSetRule::weights)
    ).apply(instance, SkyvoidStructureSetRule::new));

    public SkyvoidStructureSetRule {
        if (!java.util.Set.of("very_rare", "rare", "normal", "common", "very_common")
                .contains(density)) {
            throw new IllegalArgumentException("Unsupported structure density: " + density);
        }
        enabled = Map.copyOf(enabled);
        weights = Map.copyOf(weights);
        for (int weight : weights.values()) {
            if (weight < 1 || weight > 1000) {
                throw new IllegalArgumentException("Structure weight must be 1..1000");
            }
        }
    }
}
