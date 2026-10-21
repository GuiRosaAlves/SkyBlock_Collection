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
    public static final int CURRENT_VERSION = 1;

    public static final Codec<SkyvoidWorldProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", CURRENT_VERSION).forGetter(p -> p.schemaVersion),
            Codec.STRING.optionalFieldOf("origin", "uninitialized").forGetter(p -> p.origin),
            Codec.STRING.optionalFieldOf("worldgen", "").forGetter(p -> p.worldgen),
            Codec.STRING.optionalFieldOf("island", "").forGetter(p -> p.island),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("optional_packs", Map.of()).forGetter(p -> p.optionalPacks),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("additions", Map.of()).forGetter(p -> p.additions),
            Codec.BOOL.optionalFieldOf("migration_complete", false).forGetter(p -> p.migrationComplete)
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

    public SkyvoidWorldProfile() {
        this(CURRENT_VERSION, "uninitialized", "", "", Map.of(), Map.of(), false);
    }

    private SkyvoidWorldProfile(int schemaVersion, String origin, String worldgen, String island,
                                Map<String, Boolean> optionalPacks, Map<String, Boolean> additions,
                                boolean migrationComplete) {
        this.schemaVersion = schemaVersion;
        this.origin = Objects.requireNonNull(origin);
        this.worldgen = Objects.requireNonNull(worldgen);
        this.island = Objects.requireNonNull(island);
        this.optionalPacks = Map.copyOf(optionalPacks);
        this.additions = Map.copyOf(additions);
        this.migrationComplete = migrationComplete;
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

    public void markMigrationComplete() {
        if (!migrationComplete) {
            migrationComplete = true;
            setDirty();
        }
    }
}
