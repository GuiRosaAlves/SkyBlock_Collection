package io.github.bpr02.skyvoid;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

/** Entry point for the single-file SkyVoid distribution. */
@Mod(SkyVoid.MOD_ID)
public final class SkyVoid {
    public static final String MOD_ID = "skyvoid";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyVoid(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SkyVoidConfig.COMMON_SPEC, "skyvoid-common.toml");
        container.registerConfig(ModConfig.Type.SERVER, SkyVoidConfig.SERVER_SPEC, "skyvoid-server.toml");
        modBus.addListener(this::addBuiltInPacks);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SkyVoidClient.registerConfigScreen(container);
        }
        SkyVoidServer.register();
    }

    private void addBuiltInPacks(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            for (SkyVoidCatalog.PackDefinition pack : SkyVoidCatalog.PACKS) {
                event.addPackFinders(
                        Identifier.fromNamespaceAndPath(MOD_ID, "data/skyvoid/datapacks/" + pack.id()),
                        PackType.SERVER_DATA,
                        Component.literal("SkyVoid: " + pack.name()),
                        PackSource.BUILT_IN,
                        false,
                        Pack.Position.TOP);
            }
        }
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            for (SkyVoidCatalog.ResourcePackDefinition pack : SkyVoidCatalog.RESOURCE_PACKS) {
                event.addPackFinders(
                        Identifier.fromNamespaceAndPath(MOD_ID, "assets/skyvoid/resourcepacks/" + pack.id()),
                        PackType.CLIENT_RESOURCES,
                        Component.literal("SkyVoid: " + pack.name()),
                        PackSource.BUILT_IN,
                        false,
                        Pack.Position.TOP);
            }
        }
    }
}
