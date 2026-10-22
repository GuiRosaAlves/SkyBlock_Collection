package io.github.guirosaalves.skyvoid;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.Objects;

/**
 * Server-registry-driven structure inventory, including datapack/mod-added
 * entries. Never rely on a hardcoded vanilla list for Void with Structures.
 *
 * Inventory by itself does not mean an entry is eligible for any biome:
 * dimension, biome holder set, and actual start-generation checks belong to
 * the worldgen hook, not to this discovery pass.
 */
public final class SkyvoidVoidStructureCatalog {
    private SkyvoidVoidStructureCatalog() {}

    public static List<String> discover(MinecraftServer server) {
        Objects.requireNonNull(server);
        var registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return registry.listElements()
                .map(holder -> holder.key().identifier().toString())
                .distinct()
                .sorted()
                .toList();
    }
}
