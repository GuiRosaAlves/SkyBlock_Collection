package io.github.bpr02.skyvoid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuration exposed through NeoForge's built-in Mods-screen editor. */
public final class SkyVoidConfig {
    private static final Set<String> ADDITIONS_ENABLED_BY_DEFAULT = Set.of(
            "amethyst_geode_creation", "armor_chipping", "armor_trim_armorer_gift", "armored_nautilus_jockeys",
            "bastion_loot_gravel", "breeze_from_frozen_blaze", "calcite_from_dead_coral", "cave_spiders_in_cobwebs",
            "chamber_sherd_mason_gift", "chorus_from_endermites", "cocoa_beans_from_fishing", "coral_growth",
            "dead_bushes_from_fishing", "deepslate_from_tuff", "echo_shards_from_amethyst",
            "elder_guardians_from_lightning", "enchanted_gapples_from_cats", "end_stone_from_endermites",
            "glow_lichen_from_fishing", "gold_block_bartering", "heart_of_the_sea_from_elder_guardians",
            "more_discs_from_vaults", "netherite_brutes", "netherite_template_piglins", "netherrack_portals",
            "nylium_portals", "ores_from_magma_cubes", "sand_from_burnt_husks", "sand_from_gravel",
            "small_dripleaf_sheared", "sniffer_eggs_from_drowneds", "spore_blossom_moss", "suspicious_sniffers",
            "sweet_berries_from_foxes", "swift_sneak_librarian_gift", "trader_additions", "traders_with_camels",
            "trial_spawner_from_sculk", "tuff_from_magma", "vaults_from_ominous_raid_captains");

    private static final List<String> ADDITIONS = List.of(
            "allay_conversion", "amethyst_geode_creation", "armor_chipping", "armor_trim_armorer_gift", "armored_nautilus_jockeys", "bastion_loot_gravel", "breeze_from_frozen_blaze", "calcite_from_dead_coral", "cave_spiders_in_cobwebs", "chamber_sherd_mason_gift", "chamber_sherds_from_traders", "chorus_from_endermites", "cobwebs_from_spiders", "cocoa_beans_from_fishing", "coral_growth", "dead_bushes_from_fishing", "deepslate_from_tuff", "diamonds_from_coal_blocks", "dragon_egg_drop", "dragon_elytra_drop", "dragon_head_drop", "echo_shards_from_amethyst", "elder_guardians_from_lightning", "enchanted_gapples_from_cats", "enchanted_gapples_from_traders", "end_phantoms", "end_stone_from_endermites", "glow_lichen_from_fishing", "gold_block_bartering", "grass_from_traders", "heart_of_the_sea_from_elder_guardians", "heart_of_the_sea_from_fishing", "heavy_core_from_winded_anvil", "keys_from_ominous_raid_captains", "lava_from_traders", "lava_toolsmith_gift", "more_discs_from_vaults", "nether_wart_from_bartering", "netherite_brutes", "netherite_template_piglins", "netherrack_from_bartering", "netherrack_portals", "nylium_from_bartering", "nylium_portals", "ores_from_magma_cubes", "portal_frame_from_enderman", "pottery_sherd_mason_gift", "sand_from_burnt_husks", "sand_from_gravel", "sand_from_husk_kill", "sandstone_from_packing_sand", "sandstone_mason_trade", "shriekers_from_enderman", "shulkers_from_lightning", "silverfish_from_lightning", "small_dripleaf_sheared", "sniffer_eggs_from_drowneds", "sniffer_eggs_from_traders", "spore_blossom_moss", "suspicious_sniffers", "sweet_berries_from_foxes", "sweet_berries_from_traders", "swift_sneak_librarian_gift", "trader_additions", "traders_with_camels", "trial_spawner_from_sculk", "tuff_from_magma", "vaults_from_ominous_raid_captains");

    public static final ModConfigSpec COMMON_SPEC;
    public static final Map<String, ModConfigSpec.BooleanValue> ADDITIONS_SETTINGS;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_RULES;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        Map<String, ModConfigSpec.BooleanValue> settings = new LinkedHashMap<>();
        common.push("skyvoid_additions");
        for (String id : ADDITIONS) {
            settings.put(id, common.translation("skyvoid.config.additions." + id).define(id, ADDITIONS_ENABLED_BY_DEFAULT.contains(id)));
        }
        common.pop();
        COMMON_SPEC = common.build();
        ADDITIONS_SETTINGS = Map.copyOf(settings);

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("structure_rules");
        STRUCTURE_RULES = server.comment("Each rule is structure_id=enabled,density,weight. Density adjusts spacing; weight applies only where Minecraft selects alternatives from the same structure set.")
                .defineListAllowEmpty("rules", List.of(
                        "minecraft:ancient_city=true,0.35,1",
                        "minecraft:trial_chambers=true,0.5,1",
                        "minecraft:village_plains=true,0.5,1"), value -> value instanceof String rule && rule.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=(true|false),[0-9]+(\\.[0-9]+)?,[0-9]+"));
        server.pop();
        SERVER_SPEC = server.build();
    }

    private SkyVoidConfig() {}
}
