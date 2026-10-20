package io.github.bpr02.skyvoid;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Applies the global additions config once a world and its selected datapacks have loaded. */
public final class SkyVoidServer {
    private SkyVoidServer() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkyVoidServer::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        for (var entry : SkyVoidConfig.ADDITIONS_SETTINGS.entrySet()) {
            int enabled = entry.getValue().get() ? 1 : 0;
            server.getCommands().performPrefixedCommand(source, "scoreboard players set " + entry.getKey() + " skyvoid_config " + enabled);
        }
        server.getCommands().performPrefixedCommand(source, "scoreboard players set ominous_raid_captains skyvoid_config 0");
        server.getCommands().performPrefixedCommand(source, "execute if score vaults_from_ominous_raid_captains skyvoid_config matches 1 run scoreboard players set ominous_raid_captains skyvoid_config 1");
        server.getCommands().performPrefixedCommand(source, "execute if score keys_from_ominous_raid_captains skyvoid_config matches 1 run scoreboard players set ominous_raid_captains skyvoid_config 1");
        server.getCommands().performPrefixedCommand(source, "function skyvoid_additions:technical/load");
        SkyVoid.LOGGER.info("Applied SkyVoid Additions settings for {}.", server.getWorldData().getLevelName());
    }
}
