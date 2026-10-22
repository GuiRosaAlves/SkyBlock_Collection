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
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.server.MinecraftServer;
import java.util.LinkedHashMap;

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

    // Only the logical server may apply world-scoped settings. A remote client
    // must not attempt to apply its local COMMON config to a multiplayer server.
    private volatile MinecraftServer activeServer;
    private Map<String, Boolean> appliedAdditions = Map.of();

    private static final Map<String, String> COMPAT_MODS = Map.of(
            "skyvoid_compat_amplified_nether", "amplified_nether",
            "skyvoid_compat_nullscape", "nullscape",
            "skyvoid_compat_terralith", "terralith"
    );

    public SkyvoidMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SkyvoidConfig.SPEC);
        modBus.addListener(this::addPackFinders);
        modBus.addListener(this::onConfigReloaded);
        NeoForge.EVENT_BUS.register(this);
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            // Every new world needs exactly one worldgen and one starter island.
            addDataPack(event, SkyvoidConfig.WORLDGEN.get());
            addDataPack(event, SkyvoidConfig.ISLAND.get());

            // Overlay selected structure sets at registry-load time.
            // No pack is registered for untouched defaults.
            SkyvoidStructurePack.register(event);

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
     * NeoForge emits config reloads on the mod bus, potentially off-thread.
     * The config screen saves the COMMON spec on Done; marshal changes onto
     * the logical server, so dedicated servers and integrated servers share
     * one safe application path. No active server means future-world defaults.
     */
    private void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (!MOD_ID.equals(event.getConfig().getModId())
                || event.getConfig().getType() != ModConfig.Type.COMMON) {
            return;
        }
        MinecraftServer server = activeServer;
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (activeServer == server && SkyvoidConfig.OPTIONAL.get("skyvoid_additions").get()) {
                applyAdditions(server, snapshotAdditions());
            }
        });
    }

    private static Map<String, Boolean> snapshotAdditions() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        SkyvoidConfig.ADDITIONS.forEach((key, setting) -> values.put(key, setting.get()));
        return Map.copyOf(values);
    }

    private void applyAdditions(MinecraftServer server, Map<String, Boolean> desired) {
        // Persist live edits for worlds that already have an imported profile.
        // Uninitialized profiles are deliberately left untouched until the
        // migration stage can determine whether this is a legacy datapack save.
        var profile = SkyvoidWorldProfile.forServer(server);
        if (profile.initialized()) {
            profile.changeAdditions(desired);
        }
        var commands = server.getCommands();
        var source = server.createCommandSourceStack();
        for (String command : SkyvoidAdditionsCommands.changes(appliedAdditions, desired)) {
            commands.performPrefixedCommand(source, command);
        }
        appliedAdditions = desired;
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            activeServer = null;
            appliedAdditions = Map.of();
        }
    }

    /**
     * For a new world, initialize the scoreboard after datapack load functions
     * have created the objectives. PR #3 migration will replace global defaults
     * here with the world's stored state for an existing save.
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        activeServer = event.getServer();
        appliedAdditions = Map.of();
        if (SkyvoidConfig.OPTIONAL.get("skyvoid_additions").get()) {
            // An imported/initialized world keeps its own values across reloads
            // even when global COMMON defaults have changed.
            var profile = SkyvoidWorldProfile.forServer(event.getServer());
            if (profile.initialized()) {
                applyAdditions(event.getServer(), profile.additions());
            } else if (!SkyvoidLegacyPackDetector.hasExternalAdditions(
                    event.getServer().getPackRepository().getSelectedIds())) {
                applyAdditions(event.getServer(), snapshotAdditions());
            }
            // If the existing world has a standalone Additions ZIP enabled,
            // leave its scoreboards entirely alone until migration imports
            // those values. Saving an explicit change in Mods config still
            // applies it through onConfigReloaded().
        }
    }
}
