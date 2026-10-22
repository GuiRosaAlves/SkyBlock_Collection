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
    private static Map<String, Integer> lastEditedCommonWeights = Map.of();

    private SkyvoidVoidStructureRuntime() {}

    public static Settings resolve(SkyvoidWorldProfile profile, boolean enabled,
                                   String defaultWorldgen, Map<String, Integer> defaults) {
        if (profile.initialized()) {
            return new Settings(enabled, profile.worldgen(), profile.voidStructureWeights());
        }
        return new Settings(enabled, defaultWorldgen, defaults);
    }

    public static void activate(SkyvoidWorldProfile profile) {
        lastEditedCommonWeights = SkyvoidConfig.rawVoidStructureWeights();
        active = resolve(profile, SkyvoidConfig.VOID_STRUCTURE_REPLACEMENT_ENABLED.get(),
                SkyvoidConfig.WORLDGEN.get(), SkyvoidConfig.voidStructureWeightOverrides());
    }

    /**
     * Called on the logical server thread after a COMMON config save. Only
     * an actual weight-list edit changes the active world's persistent data;
     * editing Additions or other global fields cannot reset saved weights.
     * The already-active chunk generator snapshot is left alone until reload.
     */
    public static void persistExplicitWeightChanges(SkyvoidWorldProfile profile) {
        var latest = SkyvoidConfig.rawVoidStructureWeights();
        if (!latest.equals(lastEditedCommonWeights)) {
            if (profile.initialized()
                    && "skyvoid_worldgen_structures".equals(profile.worldgen())) {
                profile.changeVoidStructureWeights(latest);
            }
            lastEditedCommonWeights = latest;
        }
    }

    public static void deactivate() {
        active = null;
        lastEditedCommonWeights = Map.of();
    }

    public static Settings current() {
        var snapshot = active;
        return snapshot != null ? snapshot : new Settings(
                SkyvoidConfig.VOID_STRUCTURE_REPLACEMENT_ENABLED.get(),
                SkyvoidConfig.WORLDGEN.get(),
                SkyvoidConfig.voidStructureWeightOverrides());
    }
}
