package io.github.guirosaalves.skyvoid;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Only loaded on the physical client; dedicated servers must not reference
 * Minecraft client GUI classes.
 */
@Mod(value = SkyvoidMod.MOD_ID, dist = Dist.CLIENT)
public final class SkyvoidModClient {
    public SkyvoidModClient(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(container, parent)
        );
    }
}
