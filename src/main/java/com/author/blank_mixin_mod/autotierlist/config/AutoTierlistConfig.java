package com.author.blank_mixin_mod.autotierlist.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = "blank_mixin_mod", bus = Mod.EventBusSubscriber.Bus.MOD)
public class AutoTierlistConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Generation settings
    public static final ForgeConfigSpec.BooleanValue AUTO_GENERATE_ON_START;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WEAPON_TIERLIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ARMOR_TIERLIST;

    // Progression settings
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROGRESSION_ALIGNMENT;

    // Layout settings
    //public static final ForgeConfigSpec.IntValue ROWS_PER_TIER;
    public static final ForgeConfigSpec.DoubleValue QUEST_SPACING_X;
    public static final ForgeConfigSpec.DoubleValue QUEST_SPACING_Y;
    public static final ForgeConfigSpec.DoubleValue TIER_SPACING_Y;

    // Override lists
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAPON_TIER_OVERRIDES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARMOR_TIER_OVERRIDES;

    // Item filtering
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAPON_TAGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARMOR_TAGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WEAPON_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARMOR_ITEMS;
    public static final ForgeConfigSpec.BooleanValue USE_ATTRIBUTE_DETECTION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SKIPPED_EMI_CATEGORIES;

    // Cached values for fast access
    public static boolean autoGenerateOnStart;
    public static boolean enableWeaponTierlist;
    public static boolean enableArmorTierlist;
    public static double tierMultiplier;
    public static boolean enableProgressionAlignment;
    public static int rowsPerTier;
    public static double questSpacingX;
    public static double questSpacingY;
    public static double tierSpacingY;
    public static boolean useAttributeDetection;
    public static List<String> skippedEmiCategories;

    static {
        BUILDER.comment("Auto-Tierlist Configuration").push("autotierlist");

        // Generation settings
        AUTO_GENERATE_ON_START = BUILDER
            .comment("Automatically generate tierlists when the server starts")
            .define("autoGenerateOnStart", true);

        ENABLE_WEAPON_TIERLIST = BUILDER
            .comment("Enable weapon tierlist generation")
            .define("enableWeaponTierlist", true);

        ENABLE_ARMOR_TIERLIST = BUILDER
            .comment("Enable armor tierlist generation")
            .define("enableArmorTierlist", true);

        // Progression settings
        ENABLE_PROGRESSION_ALIGNMENT = BUILDER
            .comment("Align items in the same column if they share crafting relationships")
            .define("enableProgressionAlignment", true);

        // Layout settings
        /*ROWS_PER_TIER = BUILDER
            .comment("Number of rows per tier")
            .defineInRange("rowsPerTier", 1, 1, 10);*/

        QUEST_SPACING_X = BUILDER
            .comment("Horizontal spacing between quests")
            .defineInRange("questSpacingX", 1, 0.5, 5.0);

        QUEST_SPACING_Y = BUILDER
            .comment("Vertical spacing between quests")
            .defineInRange("questSpacingY", 1, 0.5, 5.0);

        TIER_SPACING_Y = BUILDER
            .comment("Extra vertical spacing between tiers")
            .defineInRange("tierSpacingY", 1, 0.0, 10.0);

        // Override lists
        WEAPON_TIER_OVERRIDES = BUILDER
            .comment("Manual tier overrides for weapons",
                     "Format: \"modid:itemname=tier\"",
                     "Example: \"minecraft:wooden_sword=0\"")
            .defineListAllowEmpty(List.of("weaponTierOverrides"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        ARMOR_TIER_OVERRIDES = BUILDER
            .comment("Manual tier overrides for armor pieces",
                     "Format: \"modid:itemname=tier\"",
                     "Example: \"minecraft:leather_helmet=0\"")
            .defineListAllowEmpty(List.of("armorTierOverrides"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        // Item filtering
        WEAPON_TAGS = BUILDER
            .comment("Item tags to include in weapon tierlist",
                     "Format: \"namespace:tagname\"",
                     "Example: \"forge:tools/swords\", \"minecraft:swords\"",
                     "Leave empty to use attribute detection only")
            .defineListAllowEmpty(List.of("weaponTags"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        ARMOR_TAGS = BUILDER
            .comment("Item tags to include in armor tierlist",
                     "Format: \"namespace:tagname\"",
                     "Example: \"forge:armors\", \"minecraft:armor\"",
                     "Leave empty to use attribute detection only")
            .defineListAllowEmpty(List.of("armorTags"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        WEAPON_ITEMS = BUILDER
            .comment("Manually specified items to include in weapon tierlist",
                     "Format: \"modid:itemname\"",
                     "Example: \"minecraft:diamond_sword\", \"twilightforest:fiery_sword\"")
            .defineListAllowEmpty(List.of("weaponItems"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        ARMOR_ITEMS = BUILDER
            .comment("Manually specified items to include in armor tierlist",
                     "Format: \"modid:itemname\"",
                     "Example: \"minecraft:diamond_chestplate\"")
            .defineListAllowEmpty(List.of("armorItems"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        USE_ATTRIBUTE_DETECTION = BUILDER
            .comment("Use attribute-based detection (attack damage > 0 for weapons, armor > 0 for armor)",
                     "If true, items with appropriate attributes will be included automatically",
                     "If false, only items matching tags or manual lists will be included")
            .define("useAttributeDetection", true);

        SKIPPED_EMI_CATEGORIES = BUILDER
            .comment("EMI recipe categories to skip when building crafting chains",
                     "Format: \"modid:category_id\"",
                     "Example: \"emi:smithing_trim\" to skip armor trim recipes",
                     "Common categories to skip:",
                     "  - emi:anvil_repairing (anvil repairs)")
            .defineListAllowEmpty(List.of("skippedEmiCategories"),
                                 () -> List.of("emi:anvil_repairing"),
                                 obj -> obj instanceof String);

        BUILDER.pop();

        tierMultiplier = 1.6;
        rowsPerTier = 1;
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        refreshCachedValues();
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        refreshCachedValues();
    }

    /**
     * Refresh all cached config values from the config spec.
     * Called automatically on config load/reload, or can be called manually.
     */
    public static void refreshCachedValues() {
        autoGenerateOnStart = AUTO_GENERATE_ON_START.get();
        enableWeaponTierlist = ENABLE_WEAPON_TIERLIST.get();
        enableArmorTierlist = ENABLE_ARMOR_TIERLIST.get();
        enableProgressionAlignment = ENABLE_PROGRESSION_ALIGNMENT.get();
//        rowsPerTier = ROWS_PER_TIER.get();
        questSpacingX = QUEST_SPACING_X.get();
        questSpacingY = QUEST_SPACING_Y.get();
        tierSpacingY = TIER_SPACING_Y.get();
        useAttributeDetection = USE_ATTRIBUTE_DETECTION.get();
        skippedEmiCategories = List.copyOf(SKIPPED_EMI_CATEGORIES.get());
    }
}
