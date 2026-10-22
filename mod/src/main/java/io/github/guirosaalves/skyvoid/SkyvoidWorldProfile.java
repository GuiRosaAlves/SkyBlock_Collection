package io.github.guirosaalves.skyvoid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;
import java.util.Objects;

/**
 * Server-owned world-specific SkyVoid profile. Stored in the save directory,
 * independently of global COMMON defaults. Never auto-initialize this while
 * reading a legacy save: migration must inspect existing datapacks first.
 *
 * The selected built-in pack identities also need an early-readable mirror
 * before dynamic registries are loaded; SavedData is not available that early.
 */
public final class SkyvoidWorldProfile extends SavedData {
    public static final int CURRENT_VERSION = 3;

    public static final Codec<SkyvoidWorldProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", CURRENT_VERSION).forGetter(p -> p.schemaVersion),
            Codec.STRING.optionalFieldOf("origin", "uninitialized").forGetter(p -> p.origin),
            Codec.STRING.optionalFieldOf("worldgen", "").forGetter(p -> p.worldgen),
            Codec.STRING.optionalFieldOf("island", "").forGetter(p -> p.island),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("optional_packs", Map.of()).forGetter(p -> p.optionalPacks),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("additions", Map.of()).forGetter(p -> p.additions),
            Codec.BOOL.optionalFieldOf("migration_complete", false).forGetter(p -> p.migrationComplete),
            Codec.unboundedMap(Codec.STRING, SkyvoidStructureSetRule.CODEC)
                    .optionalFieldOf("structure_sets", Map.of()).forGetter(p -> p.structureSets),
            Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 1000))
                    .optionalFieldOf("void_structure_weights", Map.of()).forGetter(p -> p.voidStructureWeights)
    ).apply(instance, SkyvoidWorldProfile::new));

    public static final SavedDataType<SkyvoidWorldProfile> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SkyvoidMod.MOD_ID, "world_profile"),
            SkyvoidWorldProfile::new,
            CODEC
    );

    private int schemaVersion;
    private String origin;
    private String worldgen;
    private String island;
    private Map<String, Boolean> optionalPacks;
    private Map<String, Boolean> additions;
    private boolean migrationComplete;
    private Map<String, SkyvoidStructureSetRule> structureSets;
    private Map<String, Integer> voidStructureWeights;

    public SkyvoidWorldProfile() {
        this(CURRENT_VERSION, "uninitialized", "", "", Map.of(), Map.of(), false, Map.of(), Map.of());
    }

    private SkyvoidWorldProfile(int schemaVersion, String origin, String worldgen, String island,
                                Map<String, Boolean> optionalPacks, Map<String, Boolean> additions,
                                boolean migrationComplete,
                                Map<String, SkyvoidStructureSetRule> structureSets,
                                Map<String, Integer> voidStructureWeights) {
        this.schemaVersion = schemaVersion;
        this.origin = Objects.requireNonNull(origin);
        this.worldgen = Objects.requireNonNull(worldgen);
        this.island = Objects.requireNonNull(island);
        this.optionalPacks = Map.copyOf(optionalPacks);
        this.additions = Map.copyOf(additions);
        this.migrationComplete = migrationComplete;
        this.structureSets = Map.copyOf(structureSets);
        this.voidStructureWeights = Map.copyOf(voidStructureWeights);
    }

    /** Call on the logical server thread only. */
    public static SkyvoidWorldProfile forServer(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean initialized() {
        return !"uninitialized".equals(origin);
    }

    public String origin() { return origin; }
    public String worldgen() { return worldgen; }
    public String island() { return island; }
    public Map<String, Boolean> optionalPacks() { return optionalPacks; }
    public Map<String, Boolean> additions() { return additions; }
    public boolean migrationComplete() { return migrationComplete; }
    public Map<String, SkyvoidStructureSetRule> structureSets() { return structureSets; }
    public Map<String, Integer> voidStructureWeights() { return voidStructureWeights; }

    /**
     * Only migration/bootstrap may initialize a world. This is deliberately
     * separate from reading COMMON config, avoiding unintentional overwrites.
     */
    public void initialize(String origin, String worldgen, String island,
                           Map<String, Boolean> optionalPacks, Map<String, Boolean> additions,
                           boolean migrationComplete) {
        if (initialized()) {
            throw new IllegalStateException("SkyVoid world profile is already initialized");
        }
        if (!"mod_new_world".equals(origin) && !"legacy_datapack".equals(origin)) {
            throw new IllegalArgumentException("Unsupported world profile origin: " + origin);
        }
        if (worldgen.isBlank() || island.isBlank()) {
            throw new IllegalArgumentException("Worldgen and island must be explicit");
        }
        this.origin = origin;
        this.worldgen = worldgen;
        this.island = island;
        this.optionalPacks = Map.copyOf(optionalPacks);
        this.additions = Map.copyOf(additions);
        this.migrationComplete = migrationComplete;
        setDirty();
    }

    /** Runtime changes update only the active world's Additions snapshot. */
    public void changeAdditions(Map<String, Boolean> updated) {
        if (!initialized()) {
            throw new IllegalStateException("Initialize or migrate world profile first");
        }
        var copy = Map.copyOf(updated);
        if (!copy.equals(additions)) {
            additions = copy;
            setDirty();
        }
    }

    /**
     * Edit only the structure definitions belonging to this save. Installing
     * and activating the corresponding data pack before registry load is a
     * separate explicit action; never hot-swap active worldgen registries.
     */
    public void changeStructureSets(Map<String, SkyvoidStructureSetRule> rules) {
        if (!initialized()) {
            throw new IllegalStateException("Initialize or migrate world profile first");
        }
        Map<String, SkyvoidStructureSetRule> snapshot = Map.copyOf(rules);
        if (!structureSets.equals(snapshot)) {
            structureSets = snapshot;
            setDirty();
        }
    }

    /**
     * The Void with Structures profile uses a separate structure-ID namespace
     * and numeric weights: zero disables; 1 is the default. An absent key
     * inherits vanilla/mod structure-set behavior, not zero.
     *
     * This is a saved-world preference. Worldgen must read it at the correct
     * pre-generation phase; it is not an immediate worldgen registry mutation.
     */
    public void changeVoidStructureWeights(Map<String, Integer> weights) {
        if (!initialized()) {
            throw new IllegalStateException("Initialize or migrate world profile first");
        }
        for (var entry : weights.entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || entry.getValue() < 0 || entry.getValue() > 1000) {
                throw new IllegalArgumentException("Invalid structure weight: " + entry);
            }
        }
        var snapshot = Map.copyOf(weights);
        if (!snapshot.equals(voidStructureWeights)) {
            voidStructureWeights = snapshot;
            setDirty();
        }
    }

    public void markMigrationComplete() {
        if (!migrationComplete) {
            migrationComplete = true;
            setDirty();
        }
    }
}
