package io.github.guirosaalves.skyvoid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Predicate;

/**
 * Seed-deterministic weighted selection for Void with Structures.
 *
 * Pure policy, independent of game registry and worldgen internals:
 * an eventual worldgen adapter must feed candidate-specific dimension/biome
 * and structural viability predicates, and must replace StructureStart before
 * placement. The selector does not itself modify chunk or structure data.
 */
public final class SkyvoidVoidStructureSelector {
    private SkyvoidVoidStructureSelector() {}

    public record Context(long worldSeed, String dimension,
                          int chunkX, int chunkZ, String biomeId,
                          String originalStructureId) {
        public Context {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(biomeId);
            Objects.requireNonNull(originalStructureId);
        }
    }

    public record Candidate(String id, int weight, Predicate<Context> isEligible) {
        public Candidate {
            Objects.requireNonNull(id);
            Objects.requireNonNull(isEligible);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid structure id: " + id);
            }
            if (weight < 0 || weight > 1000) {
                throw new IllegalArgumentException("Structure weight must be 0..1000: " + id);
            }
        }
    }

    /**
     * Weighted shuffle without replacement. An attempted start may fail
     * biome, height, or generator-specific checks even after eligibility
     * checks pass, so callers can attempt the subsequent candidate.
     *
     * Order is independent of the input collection/registry iteration order,
     * but depends on the seed, original start, dimension, and chunk location.
     * Weight zero always excludes a candidate.
     */
    public static List<Candidate> ranked(Context context, Collection<Candidate> discovered) {
        Objects.requireNonNull(context);
        var remaining = new ArrayList<Candidate>();
        for (var candidate : discovered) {
            if (candidate.weight() > 0 && candidate.isEligible().test(context)) {
                remaining.add(candidate);
            }
        }
        remaining.sort(Comparator.comparing(Candidate::id));
        for (int i = 1; i < remaining.size(); i++) {
            if (remaining.get(i - 1).id().equals(remaining.get(i).id())) {
                throw new IllegalArgumentException("Duplicate structure id: " + remaining.get(i).id());
            }
        }
        var rng = new SplittableRandom(selectionSeed(context));
        var ordered = new ArrayList<Candidate>(remaining.size());
        while (!remaining.isEmpty()) {
            long total = 0;
            for (var candidate : remaining) {
                total += candidate.weight();
            }
            long roll = rng.nextLong(total);
            long cumulative = 0;
            int chosenIndex = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                cumulative += remaining.get(i).weight();
                if (roll < cumulative) {
                    chosenIndex = i;
                    break;
                }
            }
            ordered.add(remaining.remove(chosenIndex));
        }
        return List.copyOf(ordered);
    }

    /**
     * The adapter supplies a start-generation callback: first successful
     * candidate wins, without trying any candidate a second time.
     */
    public static Optional<Candidate> firstValid(
            Context context, Collection<Candidate> discovered,
            Predicate<Candidate> canGenerateAtOriginalStart) {
        for (var candidate : ranked(context, discovered)) {
            if (canGenerateAtOriginalStart.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static long selectionSeed(Context context) {
        long value = mix64(context.worldSeed() ^ 0x9E3779B97F4A7C15L);
        value = mix64(value ^ ((long) context.chunkX() * 0x632BE59BD9B4E019L));
        value = mix64(value ^ ((long) context.chunkZ() * 0x85157AF5D66D4C17L));
        value = mix64(value ^ stableHash(context.dimension()));
        return mix64(value ^ stableHash(context.originalStructureId()));
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long input) {
        long v = input;
        v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
        v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }
}
