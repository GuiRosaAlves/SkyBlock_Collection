package io.github.guirosaalves.skyvoid;

import java.util.Map;

/**
 * Immutable active-server snapshot, published on the server thread after
 * the saved world profile is available. Chunk generation may happen on
 * worker threads, so never mutate the active map in place.
 *
 * During initial spawn bootstrap (before ServerStartedEvent), the explicit
 * COMMON new-world defaults still apply. After startup an existing world's
 * own saved profile wins, including intentionally absent weight overrides.
 */
public final class SkyvoidVoidStructureRuntime {
    public record Settings(boolean enabled, String worldgen, Map<String, Integer> weights) {
        public Settings {
            weights = Map.copyOf(weights);
        }
    }

    private static volatile Settings active;

    private SkyvoidVoidStructureRuntime() {}

    public static Settings resolve(SkyvoidWorldProfile profile, boolean enabled,
                                   String defaultWorldgen, Map<String, Integer> defaults) {
        if (profile.initialized()) {
            return new Settings(enabled, profile.worldgen(), profile.voidStructureWeights());
        }
        return new Settings(enabled, defaultWorldgen, defaults);
    }

    public static void activate(SkyvoidWorldProfile profile) {
        active = resolve(profile, SkyvoidConfig.VOID_STRUCTURE_REPLACEMENT_ENABLED.get(),
                SkyvoidConfig.WORLDGEN.get(), SkyvoidConfig.voidStructureWeightOverrides());
    }

    public static void deactivate() {
        active = null;
    }

    public static Settings current() {
        var snapshot = active;
        return snapshot != null ? snapshot : new Settings(
                SkyvoidConfig.VOID_STRUCTURE_REPLACEMENT_ENABLED.get(),
                SkyvoidConfig.WORLDGEN.get(),
                SkyvoidConfig.voidStructureWeightOverrides());
    }
}
