package io.github.guirosaalves.skyvoid;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keeps NeoForge's normal config editing/undo/save semantics, but replaces the
 * two unsafe freeform ID fields with cycling selectors. COMMON TOML values
 * remain strings, so existing user settings are preserved.
 *
 * This class is physically client-only; do not reference it from SkyvoidMod.
 */
public final class SkyvoidConfigSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    public SkyvoidConfigSectionScreen(Screen parent, ModConfig.Type type,
                                      ModConfig config, Component title) {
        super(parent, type, config, title);
        this.additionCategory = null;
        this.additionQuery = "";
    }

    private final String additionCategory;
    private final String additionQuery;

    private SkyvoidConfigSectionScreen(Context parentContext, Screen parent,
                                       Map<String, Object> valueSpecs, String key,
                                       Set<? extends Entry> entries, Component title) {
        this(parentContext, parent, valueSpecs, key, entries, title, null, "");
    }

    SkyvoidConfigSectionScreen(Context parentContext, Screen parent,
                               Map<String, Object> valueSpecs, String key,
                               Set<? extends Entry> entries, Component title,
                               String additionCategory, String additionQuery) {
        super(parentContext, parent, valueSpecs, key, entries, title);
        this.additionCategory = additionCategory;
        this.additionQuery = additionQuery == null ? "" : additionQuery;
    }

    @Override
    protected Element createSection(String key, UnmodifiableConfig values,
                                    UnmodifiableConfig section) {
        if (values.isEmpty()) {
            return null;
        }
        var name = getTranslationComponent(key);
        var tooltip = getTooltipComponent(key, null);
        if ("additions".equals(key) && context.keylist().isEmpty()) {
            var launch = Button.builder(
                    Component.translatable("skyvoid.ui.browse_additions"),
                    clicked -> minecraft.gui.setScreen(new SkyvoidAdditionsBrowser(
                            this, context, values.valueMap(), key, section.entrySet())))
                    .width(Button.DEFAULT_WIDTH)
                    .tooltip(Tooltip.create(tooltip))
                    .build();
            return new Element(name, tooltip, launch, false);
        }
        var button = Button.builder(
                Component.translatable("neoforge.configuration.uitext.section",
                        Component.translatable("neoforge.configuration.uitext.sectiontext")),
                clicked -> minecraft.gui.setScreen(sectionCache.computeIfAbsent(key, k ->
                        new SkyvoidConfigSectionScreen(
                                context, this, values.valueMap(), key, section.entrySet(),
                                Component.translatable(getTranslationKey(key))).rebuild())))
                .width(Button.DEFAULT_WIDTH)
                .tooltip(Tooltip.create(tooltip))
                .build();
        return new Element(
                Component.translatable("neoforge.configuration.uitext.section", name),
                tooltip, button, false);
    }

    @Override
    protected void createResetButton() {
        // NeoForge's section-level Reset iterates all entries, even entries
        // hidden by our search/category filter. Disable that destructive
        // global action here; the unfiltered sections retain normal Reset.
        if (additionCategory == null) {
            super.createResetButton();
        }
    }

    @Override
    protected Element createBooleanValue(String key,
                                         net.neoforged.neoforge.common.ModConfigSpec.ValueSpec spec,
                                         Supplier<Boolean> source, Consumer<Boolean> target) {
        if (additionCategory != null && !SkyvoidAdditionsBrowser.matches(
                key, additionCategory, additionQuery)) {
            return null;
        }
        return super.createBooleanValue(key, spec, source, target);
    }

    @Override
    protected Element createStringValue(String key, Predicate<String> tester,
                                        Supplier<String> source, Consumer<String> target) {
        if (context.keylist().equals(List.of("structures", "biome_islands"))
                && "density".equals(key)) {
            var options = SkyvoidConfig.BIOME_ISLAND_DENSITIES;
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                    new OptionInstance<>(getTranslationKey(key), getTooltip(key, null),
                            (caption, value) -> Component.translatable("skyvoid.select.density." + value),
                            new Custom<>(options), source.get(), value -> {
                                if (!value.equals(source.get()) && tester.test(value)) {
                                    undoManager.add(previous -> {
                                        target.accept(previous);
                                        onChanged(key);
                                    }, value, previous -> {
                                        target.accept(previous);
                                        onChanged(key);
                                    }, source.get());
                                }
                            }));
        }
        if (context.keylist().equals(List.of("new_world"))) {
            List<String> options = switch (key) {
                case "worldgen" -> SkyvoidConfig.WORLDGEN_IDS;
                case "island" -> SkyvoidConfig.ISLAND_IDS;
                default -> null;
            };
            if (options != null) {
                String prefix = "skyvoid.select." + key + ".";
                return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                        new OptionInstance<>(getTranslationKey(key), getTooltip(key, null),
                                (caption, value) -> Component.translatable(prefix + value),
                                new Custom<>(options), source.get(), value -> {
                                    if (!value.equals(source.get()) && tester.test(value)) {
                                        undoManager.add(previous -> {
                                            target.accept(previous);
                                            onChanged(key);
                                        }, value, previous -> {
                                            target.accept(previous);
                                            onChanged(key);
                                        }, source.get());
                                    }
                                }));
            }
        }
        return super.createStringValue(key, tester, source, target);
    }
}
