package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defaults for creating a NEW world. A future step must persist a copy of
 * these choices in each world's saved data; do not treat common config as
 * an authoritative override of an existing world.
 */
public final class SkyvoidConfig {
    public static final List<String> WORLDGEN_IDS = List.of(
            "skyvoid_worldgen",
            "skyvoid_worldgen_empty",
            "skyvoid_worldgen_full_lava",
            "skyvoid_worldgen_normal_end",
            "skyvoid_worldgen_noxiousvex",
            "skyvoid_worldgen_pinu",
            "skyvoid_worldgen_portal_room",
            "skyvoid_worldgen_single_lava",
            "skyvoid_worldgen_structures"
    );

    public static final List<String> ISLAND_IDS = List.of(
            "skyvoid_island_standard",
            "skyvoid_island_only_dirt",
            "skyvoid_island_oneblock"
    );

    public static final List<String> OPTIONAL_IDS = List.of(
            "skyvoid_additions",
            "skyvoid_advancements",
            "skyvoid_sand_island",
            "skyvoid_biome_islands",
            "skyvoid_no_end_ship",
            "skyvoid_vanilla_oneblock_starter",
            "skyvoid_compat_amplified_nether",
            "skyvoid_compat_nullscape",
            "skyvoid_compat_terralith"
    );

    public static final List<String> BIOME_ISLAND_DENSITIES = List.of(
            "very_rare", "rare", "normal", "common", "very_common"
    );
    public static final ModConfigSpec.ConfigValue<String> BIOME_ISLAND_DENSITY;
    public static final Map<String, ModConfigSpec.BooleanValue> BIOME_ISLAND_ENABLED;
    public static final Map<String, ModConfigSpec.IntValue> BIOME_ISLAND_WEIGHTS;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> WORLDGEN;
    public static final ModConfigSpec.ConfigValue<String> ISLAND;
    public static final Map<String, ModConfigSpec.BooleanValue> OPTIONAL;
    public static final Map<String, ModConfigSpec.BooleanValue> ADDITIONS;

    private SkyvoidConfig() {
    }

    static {
        var builder = new ModConfigSpec.Builder();
        // defineInList tests absent values with null while generating a new TOML.
        // List.of(...).contains(null) throws NullPointerException; ArrayList's
        // contains(null) safely returns false so NeoForge can fill defaults.

        builder.push("new_world");
        WORLDGEN = builder.comment(
                "Default worldgen datapack for newly created worlds.",
                "WARNING: do not change the underlying worldgen of existing saves."
        ).translation("skyvoid.config.new_world.worldgen")
                .defineInList("worldgen", WORLDGEN_IDS.getFirst(),
                        new ArrayList<>(WORLDGEN_IDS));

        ISLAND = builder.comment("Default starter island datapack for new worlds.")
                .translation("skyvoid.config.new_world.island")
                .defineInList("island", ISLAND_IDS.getFirst(),
                        new ArrayList<>(ISLAND_IDS));
        builder.pop();

        Map<String, ModConfigSpec.BooleanValue> optional = new LinkedHashMap<>();
        builder.push("optional_packs");
        for (String id : OPTIONAL_IDS) {
            boolean enabled = switch (id) {
                case "skyvoid_additions", "skyvoid_advancements", "skyvoid_sand_island" -> true;
                default -> false;
            };
            optional.put(id, builder
                    .comment("Use the " + id + " built-in pack for newly created worlds.")
                    .translation("skyvoid.config.optional_packs." + id)
                    .define(id, enabled));
        }
        builder.pop();
        OPTIONAL = Map.copyOf(optional);

        Map<String, ModConfigSpec.BooleanValue> additions = new LinkedHashMap<>();
        builder.push("additions");
        for (var entry : readAdditionsDefaults().entrySet()) {
            String id = entry.getKey();
            additions.put(id, builder
                    .comment("SkyVoid Additions mechanic: " + id
                            + " (applied to its skyvoid_config scoreboard setting).")
                    .translation("skyvoid.config.additions." + id)
                    .define(id, entry.getValue().booleanValue()));
        }
        builder.pop();
        ADDITIONS = Map.copyOf(additions);

        // Structure controls are intentionally limited to real supported
        // random-spread structure sets. Strongholds and starter-island markers
        // are excluded because changing them can break world progression.
        builder.push("structures");
        builder.comment("Controls affect structure generation after reloading the world.",
                "Only newly generated chunks can use changed placements.")
                .push("biome_islands");
        BIOME_ISLAND_DENSITY = builder
                .comment("Overall frequency of biome islands. Normal preserves the datapack default.",
                        "Requires Optional Datapacks > Biome Islands to be enabled.")
                .translation("skyvoid.config.structures.biome_islands.density")
                .defineInList("density", "normal", new ArrayList<>(BIOME_ISLAND_DENSITIES));

        Map<String, ModConfigSpec.BooleanValue> enabled = new LinkedHashMap<>();
        Map<String, ModConfigSpec.IntValue> weights = new LinkedHashMap<>();
        for (var entry : readBiomeIslandWeights().entrySet()) {
            String id = entry.getKey();
            builder.push(id);
            enabled.put(id, builder
                    .comment("Allow this biome island to generate in newly explored chunks.")
                    .translation("skyvoid.config.structures.biome_islands." + id + ".enabled")
                    .define("enabled", true));
            weights.put(id, builder
                    .comment("Relative selection weight (1 to 1000).",
                             "Weights do not change overall structure placement frequency.")
                    .translation("skyvoid.config.structures.biome_islands." + id + ".weight")
                    .defineInRange("weight", entry.getValue(), 1, 1000));
            builder.pop();
        }
        BIOME_ISLAND_ENABLED = Map.copyOf(enabled);
        BIOME_ISLAND_WEIGHTS = Map.copyOf(weights);
        builder.pop(2);

        SPEC = builder.build();
    }

    private static Map<String, Boolean> readAdditionsDefaults() {
        String path = "/assets/skyvoid/config/additions-defaults.json";
        InputStream input = SkyvoidConfig.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("Missing generated resource " + path
                    + "; run the Beet staging Gradle task before launching.");
        }
        try (input; var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Boolean> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getAsBoolean());
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("SkyVoid Additions default catalog is empty.");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read SkyVoid Additions defaults.", e);
        }
    }
    private static Map<String, Integer> readBiomeIslandWeights() {
        String path = "/assets/skyvoid/config/structure-catalog.json";
        try (InputStream input = SkyvoidConfig.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing generated structure catalog: " + path);
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject set = root.getAsJsonObject("skyvoid_biome_islands")
                    .getAsJsonObject("skyvoid_biome_islands:biome_islands");
            JsonObject source = set.getAsJsonObject("structures");
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (var entry : source.entrySet()) {
                String fullId = entry.getKey();
                if (!fullId.startsWith("skyvoid_biome_islands:")) {
                    throw new IllegalStateException("Unsupported biome island id: " + fullId);
                }
                ids.put(fullId.substring("skyvoid_biome_islands:".length()),
                        entry.getValue().getAsInt());
            }
            if (ids.isEmpty()) {
                throw new IllegalStateException("Biome island structure catalog is empty");
            }
            return ids;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read generated structure catalog", e);
        }
    }

}
