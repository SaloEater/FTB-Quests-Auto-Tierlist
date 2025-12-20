package com.author.blank_mixin_mod.autotierlist.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = "blank_mixin_mod", bus = Mod.EventBusSubscriber.Bus.MOD)
public class AutoTierlistConfig {
    private static final Logger LOGGER = LogUtils.getLogger();

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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SKIPPED_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends List<String>>> TAGS;

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
    public static List<String> skippedItems;

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
            .defineInRange("questSpacingX", 1, 1, 5.0);

        QUEST_SPACING_Y = BUILDER
            .comment("Vertical spacing between quests")
            .defineInRange("questSpacingY", 1, 1, 5.0);

        TIER_SPACING_Y = BUILDER
            .comment("Extra vertical spacing between tiers")
            .defineInRange("tierSpacingY", 1, 1, 10.0);

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

        SKIPPED_ITEMS = BUILDER
            .comment("Items to completely skip during tierlist generation",
                     "Format: \"modid:itemname\"",
                     "Example: \"minecraft:wooden_sword\", \"create:copper_sword\"",
                     "These items will be excluded from both weapon and armor tierlists")
            .defineListAllowEmpty(List.of("skippedItems"),
                                 () -> List.of(),
                                 obj -> obj instanceof String);

        // Load defaults from JSON
        List<List<String>> defaultTags = new ArrayList<>();

        try {
            InputStream stream = AutoTierlistConfig.class.getClassLoader().getResourceAsStream("armageddontags_tierlist.json");
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                if (root.has("tags")) {
                    root.getAsJsonArray("tags").forEach(element -> {
                        JsonObject tagObj = element.getAsJsonObject();
                        String tag = tagObj.get("tags").getAsString();
                        String label = tagObj.get("label").getAsString();
                        String color = tagObj.get("color").getAsString();

                        defaultTags.add(Arrays.asList(tag, label, color));
                    });
                }
                LOGGER.info("Loaded {} default tag entries from armageddontags_tierlist.json", defaultTags.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load defaults from armageddontags_tierlist.json", e);
        }

        TAGS = BUILDER
                .comment("List of tag entries. Format: [tags, label letter, color]",
                        "Tags can be comma-separated for multiple tags",
                        "Example: [\"forge:diamond_tools,minecraft:swords\", \"D\", \"c\"]")
                .defineList("tags", defaultTags, obj -> {
                    if (!(obj instanceof List)) return false;
                    List<?> list = (List<?>) obj;
                    return list.size() == 4 && list.stream().allMatch(item -> item instanceof String);
                });

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

    public static List<TagEntry> getTagEntries() {
        List<TagEntry> entries = new ArrayList<>();

        for (List<String> entry : TAGS.get()) {
            if (entry.size() >= 3) {
                entries.add(new TagEntry(entry.get(0), entry.get(1).charAt(0), entry.get(2).charAt(0)));
            }
        }

        return entries;
    }

    public static class TagEntry {
        private final String tagLine;
        private final List<String> tags;
        private final char label;
        private final char color;

        public TagEntry(String tag, char label, char color) {
            this.tagLine = tag;
            this.label = label;
            this.color = color;

            // Parse comma-separated tags
            this.tags = new ArrayList<>();
            for (String t : tag.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    this.tags.add(trimmed);
                }
            }
        }

        public String getTagLine() {
            return tagLine;
        }

        public List<String> getTags() {
            return tags;
        }

        public List<ResourceLocation> getTagLocations() {
            List<ResourceLocation> locations = new ArrayList<>();
            for (String t : tags) {
                locations.add(new ResourceLocation(t));
            }
            return locations;
        }

        public char getLabel() {
            return label;
        }

        public char getColor() {
            return color;
        }

        public List<TagKey<Item>> getTagKeys() {
            List<TagKey<Item>> tagKeys = new ArrayList<>();
            for (String t : tags) {
                tagKeys.add(TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation(t)));
            }
            return tagKeys;
        }
    }

    /**
     * Refresh all cached config values from the config spec.
     * Called automatically on config load/reload, or can be called manually.
     */
    public static void refreshCachedValues() {
        AUTO_GENERATE_ON_START.clearCache();
        ENABLE_WEAPON_TIERLIST.clearCache();
        ENABLE_ARMOR_TIERLIST.clearCache();
        ENABLE_PROGRESSION_ALIGNMENT.clearCache();
//        ROWS_PER_TIER.clearCache();
        QUEST_SPACING_X.clearCache();
        QUEST_SPACING_Y.clearCache();
        TIER_SPACING_Y.clearCache();
        USE_ATTRIBUTE_DETECTION.clearCache();
        SKIPPED_EMI_CATEGORIES.clearCache();
        SKIPPED_ITEMS.clearCache();
        WEAPON_ITEMS.clearCache();
        ARMOR_ITEMS.clearCache();
        WEAPON_TAGS.clearCache();
        ARMOR_TAGS.clearCache();
        WEAPON_TIER_OVERRIDES.clearCache();
        ARMOR_TIER_OVERRIDES.clearCache();

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
        skippedItems = List.copyOf(SKIPPED_ITEMS.get());
    }
}
