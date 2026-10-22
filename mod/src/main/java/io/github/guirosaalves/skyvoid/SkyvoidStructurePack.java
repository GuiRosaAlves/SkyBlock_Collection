package io.github.guirosaalves.skyvoid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Applies structure selections as a real, high-priority SERVER_DATA pack
 * BEFORE dynamic registries load. Never reload structure sets in a live world.
 *
 * The first supported set is biome islands; progression-critical strongholds
 * and starter-island markers are deliberately not configurable.
 */
public final class SkyvoidStructurePack {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PACK_ID = "skyvoid:generated_structure_overrides";
    private static final String SOURCE = "/data/skyvoid/datapacks/skyvoid_biome_islands/";
    private static final String JSON_PATH =
            "data/skyvoid_biome_islands/worldgen/structure_set/biome_islands.json";
    private static final String PREFIX = "skyvoid_biome_islands:";

    private SkyvoidStructurePack() {}

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA
                || !SkyvoidConfig.OPTIONAL.get("skyvoid_biome_islands").get()) {
            return;
        }
        var enabled = new LinkedHashMap<String, Boolean>();
        SkyvoidConfig.BIOME_ISLAND_ENABLED.forEach((key, value) -> enabled.put(key, value.get()));
        var weights = new LinkedHashMap<String, Integer>();
        SkyvoidConfig.BIOME_ISLAND_WEIGHTS.forEach((key, value) -> weights.put(key, value.get()));
        var original = resourceJson(SOURCE + JSON_PATH);
        var edited = transform(original, enabled, weights, SkyvoidConfig.BIOME_ISLAND_DENSITY.get());
        if (original.equals(edited)) {
            return; // The original datapack must remain byte-for-byte authoritative.
        }

        Path folder = materialize(edited);
        event.addRepositorySource(consumer -> {
            var info = new PackLocationInfo(PACK_ID,
                    Component.literal("SkyVoid: Structure Overrides (New Chunks)"),
                    PackSource.BUILT_IN, Optional.empty());
            var pack = Pack.readMetaAndCreate(
                    info,
                    new PathPackResources.PathResourcesSupplier(folder),
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack == null) {
                throw new IllegalStateException("Generated SkyVoid structure override pack is invalid");
            }
            consumer.accept(pack);
        });
        LOGGER.info("Registered SkyVoid structure override pack at {}", folder);
    }

    /**
     * Pure JSON transformation so Minecraft registry loading receives the
     * same unchanged structure-set fields except configured values.
     */
    public static JsonObject transform(JsonObject original,
                                       Map<String, Boolean> enabled,
                                       Map<String, Integer> weights,
                                       String density) {
        var copy = original.deepCopy();
        var sourceEntries = original.getAsJsonArray("structures");
        var retained = new JsonArray();
        for (var value : sourceEntries) {
            var entry = value.getAsJsonObject();
            String full = entry.get("structure").getAsString();
            if (!full.startsWith(PREFIX)) {
                throw new IllegalArgumentException("Unexpected structure in biome island set: " + full);
            }
            String key = full.substring(PREFIX.length());
            if (!enabled.getOrDefault(key, true)) {
                continue;
            }
            int weight = weights.getOrDefault(key, entry.get("weight").getAsInt());
            if (weight < 1 || weight > 1000) {
                throw new IllegalArgumentException("Invalid structure weight for " + key);
            }
            var selected = entry.deepCopy();
            selected.addProperty("weight", weight);
            retained.add(selected);
        }
        var placement = copy.getAsJsonObject("placement");
        if (!"minecraft:random_spread".equals(placement.get("type").getAsString())) {
            throw new IllegalArgumentException("Only random_spread structure sets support density");
        }
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        double factor = switch (density) {
            case "very_rare" -> 2.0;
            case "rare" -> 1.5;
            case "normal" -> 1.0;
            case "common" -> 0.75;
            case "very_common" -> 0.5;
            default -> throw new IllegalArgumentException("Invalid structure density: " + density);
        };
        if (retained.isEmpty()) {
            // Minecraft rejects structure sets without entries. Preserve a
            // dummy valid entry but make generation probability zero instead.
            retained.add(sourceEntries.get(0).deepCopy());
            placement.addProperty("frequency", 0.0);
        } else if (factor != 1.0) {
            placement.addProperty("spacing",
                    Math.min(4096, Math.max(separation + 1, (int) Math.round(spacing * factor))));
        }
        copy.add("structures", retained);
        return copy;
    }

    private static JsonObject resourceJson(String name) {
        try (InputStream input = SkyvoidStructurePack.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled structure resource " + name);
            }
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled structure resource " + name, e);
        }
    }

    private static byte[] resourceBytes(String name) {
        try (InputStream input = SkyvoidStructurePack.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled datapack manifest " + name);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled datapack manifest " + name, e);
        }
    }

    private static Path materialize(JsonObject override) {
        String json = override.toString();
        byte[] metadata = resourceBytes(SOURCE + "pack.mcmeta");
        String hash = sha256(json + new String(metadata, StandardCharsets.UTF_8));
        Path root = FMLPaths.CONFIGDIR.get().resolve("skyvoid/structure-overrides");
        Path folder = root.resolve(hash);
        Path manifest = folder.resolve("pack.mcmeta");
        if (Files.isRegularFile(manifest)) {
            return folder;
        }
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempDirectory(root, "staging-");
            try {
                Path target = temporary.resolve(JSON_PATH);
                Files.createDirectories(target.getParent());
                Files.writeString(target, json, StandardCharsets.UTF_8);
                Files.write(temporary.resolve("pack.mcmeta"), metadata);
                try {
                    Files.move(temporary, folder, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, folder);
                }
            } finally {
                // Only leftover staging directories are removed; published
                // hashed packs remain stable while Minecraft may be reading.
                if (Files.exists(temporary)) {
                    try (var paths = Files.walk(temporary)) {
                        paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create SkyVoid structure override datapack", e);
        }
        return folder;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
