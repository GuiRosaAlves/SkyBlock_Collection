package io.github.bpr02.skyvoid;

import java.util.List;

/** The IDs are also checked against the staged Beet artifacts by verifySkyvoidPacks. */
public final class SkyVoidCatalog {
    private SkyVoidCatalog() {}

    public record PackDefinition(String id, String name, Kind kind, boolean standardDefault) {}
    public record ResourcePackDefinition(String id, String name, String companionDatapack) {}
    public enum Kind { WORLDGEN, ISLAND, OPTIONAL, COMPATIBILITY }

    public static final List<PackDefinition> PACKS = List.of(
            new PackDefinition("skyvoid_worldgen", "Sky Void Worldgen", Kind.WORLDGEN, true),
            new PackDefinition("skyvoid_worldgen_empty", "Sky Void Empty", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_full_lava", "Sky Void Full Lava", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_normal_end", "Sky Void Normal End", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_noxiousvex", "Sky Void Noxious Vex", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_pinu", "Sky Void Pinu", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_portal_room", "Sky Void Portal Room", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_single_lava", "Sky Void Single Lava", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_worldgen_structures", "Sky Void Structures", Kind.WORLDGEN, false),
            new PackDefinition("skyvoid_island_standard", "Sky Void Standard Island", Kind.ISLAND, true),
            new PackDefinition("skyvoid_island_oneblock", "Sky Void One Block Island", Kind.ISLAND, false),
            new PackDefinition("skyvoid_island_only_dirt", "Sky Void Dirt Island", Kind.ISLAND, false),
            new PackDefinition("skyvoid_additions", "Sky Void Additions", Kind.OPTIONAL, false),
            new PackDefinition("skyvoid_advancements", "Sky Void Advancements", Kind.OPTIONAL, false),
            new PackDefinition("skyvoid_biome_islands", "Sky Void Biome Islands", Kind.OPTIONAL, false),
            new PackDefinition("skyvoid_no_end_ship", "Sky Void No End Ship", Kind.OPTIONAL, false),
            new PackDefinition("skyvoid_sand_island", "Sky Void Sand Island", Kind.OPTIONAL, true),
            new PackDefinition("skyvoid_vanilla_oneblock_starter", "Sky Void Vanilla Oneblock Starter", Kind.OPTIONAL, false),
            new PackDefinition("skyvoid_compat_amplified_nether", "Sky Void Amplified Nether Compatibility", Kind.COMPATIBILITY, false),
            new PackDefinition("skyvoid_compat_nullscape", "Sky Void Nullscape Compatibility", Kind.COMPATIBILITY, false),
            new PackDefinition("skyvoid_compat_terralith", "Sky Void Terralith Compatibility", Kind.COMPATIBILITY, false));

    public static final List<ResourcePackDefinition> RESOURCE_PACKS = List.of(
            new ResourcePackDefinition("rp_skyvoid_advancements", "Sky Void Advancements Resources", "skyvoid_advancements"));
}
