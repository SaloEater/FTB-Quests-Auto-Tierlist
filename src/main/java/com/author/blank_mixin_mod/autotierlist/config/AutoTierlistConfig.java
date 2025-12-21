package com.author.blank_mixin_mod.autotierlist.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
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

    // Chapter settings
    public static final ForgeConfigSpec.ConfigValue<String> WEAPON_CHAPTER_ID;
    public static final ForgeConfigSpec.ConfigValue<String> ARMOR_CHAPTER_ID;
    public static final ForgeConfigSpec.ConfigValue<String> WEAPON_CHAPTER_TITLE;
    public static final ForgeConfigSpec.ConfigValue<String> ARMOR_CHAPTER_TITLE;

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
    public static final ForgeConfigSpec.ConfigValue<List<? extends List<String>>> ARMAGEDDON_TAGS;

    // Constants (not from config)
    public static final double TIER_MULTIPLIER = 1.6;
    public static final int ROWS_PER_TIER = 1;

    static {
        BUILDER.comment("Auto-Tierlist Configuration").push("autotierlist");

        // Generation settings
        AUTO_GENERATE_ON_START = BUILDER
            .comment("Automatically generate tierlists when the server starts")
            .define("autoGenerateOnStart", false);

        ENABLE_WEAPON_TIERLIST = BUILDER
            .comment("Enable weapon tierlist generation")
            .define("enableWeaponTierlist", true);

        ENABLE_ARMOR_TIERLIST = BUILDER
            .comment("Enable armor tierlist generation")
            .define("enableArmorTierlist", true);

        // Chapter settings
        WEAPON_CHAPTER_ID = BUILDER
            .comment("Chapter ID for weapon tierlist")
            .define("weaponChapterId", "autotierlist_weapons");

        ARMOR_CHAPTER_ID = BUILDER
            .comment("Chapter ID for armor tierlist")
            .define("armorChapterId", "autotierlist_armor");

        WEAPON_CHAPTER_TITLE = BUILDER
            .comment("Translatable title key for weapon chapter")
            .define("weaponChapterTitle", "blank_mixin_mod.chapter.weapons.title");

        ARMOR_CHAPTER_TITLE = BUILDER
            .comment("Translatable title key for armor chapter")
            .define("armorChapterTitle", "blank_mixin_mod.chapter.armor.title");

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
                        String headerItem = tagObj.has("header_item") ? tagObj.get("header_item").getAsString() : "";
                        String headerTitle = tagObj.has("header_title") ? tagObj.get("header_title").getAsString() : "";

                        defaultTags.add(Arrays.asList(tag, label, color, headerItem, headerTitle));
                    });
                }
                LOGGER.info("Loaded {} default tag entries from armageddontags_tierlist.json", defaultTags.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load defaults from armageddontags_tierlist.json", e);
        }

        ARMAGEDDON_TAGS = BUILDER
                .comment("List of tag entries. Format: [tags, label letter, color, header_item, header_title]",
                        "Tags can be comma-separated for multiple tags",
                        "header_item: Item ID to use for header quest (optional, empty string to skip)",
                        "header_title: Translatable key for header quest title (optional, empty string to skip)",
                        "Example: [\"forge:diamond_tools,minecraft:swords\", \"D\", \"c\", \"minecraft:diamond\", \"advancements.the_diamond_keeper.descr\"]")
                .defineList("tags", defaultTags, obj -> {
                    if (!(obj instanceof List)) return false;
                    List<?> list = (List<?>) obj;
                    return (list.size() == 3 || list.size() == 5) && list.stream().allMatch(item -> item instanceof String);
                });

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static List<TagEntry> getArmageddonTagEntries() {
        List<TagEntry> entries = new ArrayList<>();

        for (List<String> entry : ARMAGEDDON_TAGS.get()) {
            if (entry.size() >= 3) {
                String headerItem = entry.size() >= 5 ? entry.get(3) : "";
                String headerTitle = entry.size() >= 5 ? entry.get(4) : "";
                entries.add(new TagEntry(entry.get(0), entry.get(1).charAt(0), entry.get(2).charAt(0), headerItem, headerTitle));
            }
        }

        return entries;
    }

    public static class TagEntry {
        private final String tagLine;
        private final List<String> tags;
        private final char label;
        private final char color;
        private final String headerItem;
        private final String headerTitle;

        public TagEntry(String tag, char label, char color, String headerItem, String headerTitle) {
            this.tagLine = tag;
            this.label = label;
            this.color = color;
            this.headerItem = headerItem;
            this.headerTitle = headerTitle;

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

        public String getHeaderItem() {
            return headerItem;
        }

        public String getHeaderTitle() {
            return headerTitle;
        }

        public boolean hasHeader() {
            return !headerItem.isEmpty() && !headerTitle.isEmpty();
        }
    }
}
