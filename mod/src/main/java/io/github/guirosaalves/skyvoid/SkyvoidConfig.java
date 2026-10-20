package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> WORLDGEN;
    public static final ModConfigSpec.ConfigValue<String> ISLAND;
    public static final Map<String, ModConfigSpec.BooleanValue> OPTIONAL;
    public static final Map<String, ModConfigSpec.BooleanValue> ADDITIONS;

    private SkyvoidConfig() {
    }

    static {
        var builder = new ModConfigSpec.Builder();

        builder.push("new_world");
        WORLDGEN = builder.comment(
                "Default worldgen datapack for newly created worlds.",
                "WARNING: do not change the underlying worldgen of existing saves."
        ).translation("skyvoid.config.new_world.worldgen")
                .defineInList("worldgen", WORLDGEN_IDS.getFirst(), WORLDGEN_IDS);

        ISLAND = builder.comment("Default starter island datapack for new worlds.")
                .translation("skyvoid.config.new_world.island")
                .defineInList("island", ISLAND_IDS.getFirst(), ISLAND_IDS);
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
}
