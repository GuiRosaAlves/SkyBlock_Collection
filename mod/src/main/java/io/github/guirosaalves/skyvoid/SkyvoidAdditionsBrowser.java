package io.github.guirosaalves.skyvoid;

import com.electronwill.nightconfig.core.UnmodifiableConfig.Entry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Category/search landing page for the Additions settings.
 *
 * The actual editing screen is a NeoForge ConfigurationSectionScreen, retaining
 * its normal undo/reset/save logic. This browser changes no server state.
 */
public final class SkyvoidAdditionsBrowser extends Screen {
    public static final String ALL = "all";
    public static final String TRADES = "trades";
    public static final String MOBS = "mobs";
    public static final String RESOURCES = "resources";
    public static final String WORLD = "world";

    private final Screen parent;
    private final Context context;
    private final Map<String, Object> specs;
    private final String key;
    private final Set<? extends Entry> entries;
    private String search = "";

    SkyvoidAdditionsBrowser(Screen parent, Context context, Map<String, Object> specs,
                            String key, Set<? extends Entry> entries) {
        super(Component.translatable("skyvoid.ui.additions_browser"));
        this.parent = parent;
        this.context = context;
        this.specs = specs;
        this.key = key;
        this.entries = entries;
    }

    @Override
    protected void init() {
        int left = width / 2 - 154;
        int top = Math.max(44, height / 2 - 110);
        var query = new EditBox(font, left, top, 308, 20,
                Component.translatable("skyvoid.ui.search"));
        query.setMaxLength(128);
        query.setValue(search);
        query.setResponder(text -> search = text);
        addRenderableWidget(query);

        String[] ids = {ALL, TRADES, MOBS, RESOURCES, WORLD};
        for (int i = 0; i < ids.length; i++) {
            String category = ids[i];
            addRenderableWidget(Button.builder(
                    Component.translatable("skyvoid.ui.category." + category),
                    button -> openCategory(category))
                    .bounds(left + (i % 2) * 158, top + 30 + (i / 2) * 28,
                            i == 4 ? 308 : 150, 20).build());
        }
        addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                button -> onClose()).bounds(left, top + 122, 308, 20).build());
    }

    private void openCategory(String category) {
        minecraft.gui.setScreen(new SkyvoidConfigSectionScreen(
                context, this, specs, key, entries,
                Component.translatable("skyvoid.ui.category." + category),
                category, search.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    public static boolean matches(String id, String category, String query) {
        String group = categoryOf(id);
        if (!ALL.equals(category) && !group.equals(category)) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        String search = query.toLowerCase(Locale.ROOT);
        String name = Component.translatable("skyvoid.config.additions." + id)
                .getString().toLowerCase(Locale.ROOT);
        return id.contains(search) || name.contains(search);
    }

    /**
     * Stable UI-only grouping; intentionally does not change saved TOML keys.
     * Unknown features fall into World & Other instead of being hidden.
     */
    public static String categoryOf(String id) {
        if (id.contains("trader") || id.contains("barter") || id.contains("gift")
                || id.contains("mason_trade") || id.contains("toolsmith")) {
            return TRADES;
        }
        if (id.contains("portal") || id.contains("trial_spawner")
                || id.contains("vaults_") || id.contains("netherrack_portals")
                || id.contains("nylium_portals")) {
            return WORLD;
        }
        if (id.contains("spider") || id.contains("enderman") || id.contains("endermite")
                || id.contains("guardian") || id.contains("shulker")
                || id.contains("sniffer") || id.contains("foxes") || id.contains("cats")
                || id.contains("husk") || id.contains("phantom")
                || id.contains("jockey") || id.contains("allay")
                || id.contains("blaze") || id.contains("brute")
                || id.contains("piglin") || id.contains("silverfish")
                || id.contains("magma_cubes")) {
            return MOBS;
        }
        return RESOURCES;
    }
}
