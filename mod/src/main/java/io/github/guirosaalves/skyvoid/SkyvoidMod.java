package io.github.guirosaalves.skyvoid;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Map;

/**
 * EARLY PROTOTYPE: new-world defaults and scoreboard settings only.
 *
 * IMPORTANT: Persist the pack selection in world-specific saved data before
 * publishing this as a user-facing mod. Changing global defaults must not
 * silently migrate previously generated worlds.
 */
@Mod(SkyvoidMod.MOD_ID)
public final class SkyvoidMod {
    public static final String MOD_ID = "skyvoid";

    private static final Map<String, String> COMPAT_MODS = Map.of(
            "skyvoid_compat_amplified_nether", "amplified_nether",
            "skyvoid_compat_nullscape", "nullscape",
            "skyvoid_compat_terralith", "terralith"
    );

    public SkyvoidMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SkyvoidConfig.SPEC);
        modBus.addListener(this::addPackFinders);
        NeoForge.EVENT_BUS.register(this);
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            // Every new world needs exactly one worldgen and one starter island.
            addDataPack(event, SkyvoidConfig.WORLDGEN.get());
            addDataPack(event, SkyvoidConfig.ISLAND.get());

            for (var entry : SkyvoidConfig.OPTIONAL.entrySet()) {
                if (!entry.getValue().get()) {
                    continue;
                }
                String requiredMod = COMPAT_MODS.get(entry.getKey());
                if (requiredMod != null && !ModList.get().isLoaded(requiredMod)) {
                    continue;
                }
                addDataPack(event, entry.getKey());
            }
        } else if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            // Keep RP distinct from the advancement datapack.
            if (SkyvoidConfig.OPTIONAL.get("skyvoid_advancements").get()) {
                event.addPackFinders(
                        Identifier.fromNamespaceAndPath(
                                MOD_ID, "assets/skyvoid/resourcepacks/rp_skyvoid_advancements"),
                        PackType.CLIENT_RESOURCES,
                        Component.literal("SkyVoid Advancements Resources"),
                        PackSource.BUILT_IN,
                        true,
                        Pack.Position.TOP
                );
            }
        }
    }

    private void addDataPack(AddPackFindersEvent event, String id) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(MOD_ID, "data/skyvoid/datapacks/" + id),
                PackType.SERVER_DATA,
                Component.literal("SkyVoid: " + id),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP
        );
    }

    /**
     * Apply the pre-world config only after the datapack created its
     * skyvoid_config scoreboard objective; then refresh scheduled mechanics.
     * This currently runs at EACH server start, including existing worlds:
     * per-world persistence must be added before a stable release.
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!SkyvoidConfig.OPTIONAL.get("skyvoid_additions").get()) {
            return;
        }
        var server = event.getServer();
        var commands = server.getCommands();
        var source = server.createCommandSourceStack();
        for (var entry : SkyvoidConfig.ADDITIONS.entrySet()) {
            commands.performPrefixedCommand(
                    source,
                    "scoreboard players set " + entry.getKey()
                            + " skyvoid_config " + (entry.getValue().get() ? "1" : "0")
            );
        }
        commands.performPrefixedCommand(source, "function skyvoid_additions:technical/load");
    }
}
