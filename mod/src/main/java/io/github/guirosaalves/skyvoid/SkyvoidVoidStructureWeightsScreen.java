package io.github.guirosaalves.skyvoid;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationListScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Ordered registry-driven UI: every discovered vanilla/modded structure has
 * a namespaced label and a numeric weight (0 disables, 1 vanilla default).
 *
 * The standard NeoForge ConfigurationListScreen still owns undo, reset and
 * saving to COMMON config. Discovery is available when a world is open;
 * without a loaded world the user can add namespaced entries manually.
 */
public final class SkyvoidVoidStructureWeightsScreen extends ConfigurationListScreen<String> {
    private final boolean hasLiveRegistry;

    public SkyvoidVoidStructureWeightsScreen(
            Context context, String key, Component title,
            ModConfigSpec.ListValueSpec spec,
            ModConfigSpec.ConfigValue<List<String>> valueList) {
        super(context, key, title, spec, valueList);

        var currentWorld = Minecraft.getInstance().level;
        hasLiveRegistry = currentWorld != null;
        if (currentWorld != null) {
            var registry = currentWorld.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var present = new HashSet<String>();
            for (String line : cfgList) {
                int at = line.lastIndexOf('=');
                if (at > 0) present.add(line.substring(0, at));
            }
            var discovered = registry.listElements()
                    .map(holder -> holder.key().identifier().toString())
                    .filter(id -> !id.endsWith(":stronghold") && !id.endsWith(":strongholds"))
                    .sorted()
                    .toList();
            for (String id : discovered) {
                if (present.add(id)) {
                    cfgList.add(id + "=1");
                }
            }
        }
        cfgList.sort(Comparator
                .comparingInt((String item) -> item.startsWith("minecraft:") ? 0 : 1)
                .thenComparing(item -> item.substring(0, item.lastIndexOf('='))));
    }

    @Override
    public ConfigurationSectionScreen rebuild() {
        return super.rebuild();
    }

    @Override
    protected AbstractWidget createListLabel(int idx) {
        String line = cfgList.get(idx);
        int at = line.lastIndexOf('=');
        String id = at < 0 ? line : line.substring(0, at);
        return new StringWidget(150, 20, Component.literal(id), font);
    }

    @Override
    protected Element createStringListValue(int idx, String stored) {
        int equal = stored.lastIndexOf('=');
        if (equal <= 0) {
            return super.createStringListValue(idx, stored);
        }
        String id = stored.substring(0, equal);
        String oldWeight = stored.substring(equal + 1);
        return createStringValue(key,
                value -> {
                    try {
                        int n = Integer.parseInt(value);
                        return n >= 0 && n <= 1000 && spec.test(List.of(id + "=" + value));
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                },
                () -> oldWeight,
                value -> cfgList.set(idx, id + "=" + value));
    }

    @Override
    protected void createAddElementButton() {
        // A loaded world already exposes every live registry entry. Disable
        // manual additions there to avoid unknown or duplicate structure IDs.
        if (!hasLiveRegistry) super.createAddElementButton();
    }
}
