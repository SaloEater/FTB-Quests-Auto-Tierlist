package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.analysis.ItemData;
import com.author.blank_mixin_mod.autotierlist.analysis.ItemScanner;
import com.author.blank_mixin_mod.autotierlist.analysis.TierCalculator;
import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import com.author.blank_mixin_mod.autotierlist.config.ItemFilter;
import com.author.blank_mixin_mod.autotierlist.config.TierOverrideManager;
import com.author.blank_mixin_mod.autotierlist.progression.CraftingChainDetector;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates the weapon tierlist chapter in FTBQuests.
 */
public class WeaponTierlistGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHAPTER_ID = "autotierlist_weapons";

    private final TierOverrideManager overrideManager;
    private final Map<ResourceLocation, Quest> itemToQuestMap = new HashMap<>();

    public WeaponTierlistGenerator(TierOverrideManager overrideManager) {
        this.overrideManager = overrideManager;
    }

    /**
     * Generate the weapon tierlist chapter.
     */
    public void generate(ServerQuestFile questFile, ServerLevel level) {
        LOGGER.info("Generating weapon tierlist...");

        try {
            // 1. Create and configure item filter
            ItemFilter filter = new ItemFilter(AutoTierlistConfig.useAttributeDetection);
            filter.loadWeaponTags(AutoTierlistConfig.WEAPON_TAGS.get());
            filter.loadWeaponItems(AutoTierlistConfig.WEAPON_ITEMS.get());
            LOGGER.info("Item filter configured: {}", filter.getStats());

            // 2. Scan for weapons
            ItemScanner scanner = new ItemScanner(filter);
            List<ItemData.WeaponData> weapons = scanner.scanWeapons();

            if (weapons.isEmpty()) {
                LOGGER.warn("No weapons found, skipping weapon tierlist generation");
                return;
            }

            // 3. Calculate tier assignments
            TierCalculator calculator = new TierCalculator(
                AutoTierlistConfig.tierMultiplier,
                AutoTierlistConfig.rowsPerTier,
                overrideManager
            );
            Map<Integer, List<TierCalculator.TieredItem<ItemData.WeaponData>>> tiers =
                calculator.assignWeaponTiers(weapons);

            // 4. Detect progression chains if enabled
            Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
            if (AutoTierlistConfig.enableProgressionAlignment) {
                try {
                    CraftingChainDetector detector = new CraftingChainDetector(level.getRecipeManager());
                    List<ResourceLocation> weaponIds = weapons.stream()
                        .map(ItemData.WeaponData::id)
                        .collect(Collectors.toList());
                    recipeGraph = detector.getRecipeGraph(weaponIds);

                    // Build tier map for sorting chains
                    Map<ResourceLocation, Integer> tierMap = new HashMap<>();
                    for (Map.Entry<Integer, List<TierCalculator.TieredItem<ItemData.WeaponData>>> entry : tiers.entrySet()) {
                        for (TierCalculator.TieredItem<ItemData.WeaponData> item : entry.getValue()) {
                            tierMap.put(item.data().id(), item.tier());
                        }
                    }

                    columnAssignments = assignProgressionColumns(weaponIds, recipeGraph, tierMap);
                    LOGGER.info("Assigned {} weapons to progression columns", columnAssignments.size());
                } catch (Exception e) {
                    LOGGER.error("Failed to detect progression chains, continuing without progression alignment", e);
                }
            }

            // 5. Create chapter
            ChapterGroup defaultGroup = questFile.getDefaultChapterGroup();
            if (defaultGroup == null) {
                LOGGER.error("No default chapter group found!");
                return;
            }

            Chapter chapter = new Chapter(questFile.newID(), questFile, defaultGroup, CHAPTER_ID);
            chapter.setRawTitle("Weapon Tierlist");
            chapter.onCreated();

            // 6. Generate quests for each tier
            List<Integer> sortedTiers = new ArrayList<>(tiers.keySet());
            Collections.sort(sortedTiers);

            for (int tierIndex = 0; tierIndex < sortedTiers.size(); tierIndex++) {
                int tier = sortedTiers.get(tierIndex);
                generateTierQuests(questFile, chapter, tier, tierIndex, tiers.get(tier), columnAssignments);
            }

            // 7. Create quest dependencies based on crafting relationships
            if (AutoTierlistConfig.enableProgressionAlignment && !recipeGraph.isEmpty()) {
                createQuestDependencies(recipeGraph);
            }

            LOGGER.info("Weapon tierlist generated successfully with {} tiers", tiers.size());

        } catch (Exception e) {
            LOGGER.error("Failed to generate weapon tierlist", e);
            throw new RuntimeException("Weapon tierlist generation failed", e);
        }
    }

    /**
     * Generate quests for a single tier.
     *
     * @param tier The tier number (for labeling)
     * @param tierIndex The sequential index (for positioning)
     */
    private void generateTierQuests(ServerQuestFile questFile, Chapter chapter, int tier, int tierIndex,
                                    List<TierCalculator.TieredItem<ItemData.WeaponData>> items,
                                    Map<ResourceLocation, Integer> columnAssignments) {
        // Calculate tier base Y position using sequential index, not tier number
        double tierBaseY = QuestFactory.calculateTierBaseY(
            tierIndex,
            AutoTierlistConfig.rowsPerTier,
            AutoTierlistConfig.questSpacingY,
            AutoTierlistConfig.tierSpacingY
        );

        // Calculate damage value for this tier
        double minDamage = tier * AutoTierlistConfig.tierMultiplier;
        double maxDamage = (tier + 1) * AutoTierlistConfig.tierMultiplier;
        String damageLabel = String.format("Damage: %.1f-%.1f", minDamage, maxDamage);

        // Create secret tier marker quest with damage range
        QuestFactory.createSecretTierQuest(questFile, chapter, damageLabel, tierBaseY);

        // Group items by row
        Map<Integer, List<TierCalculator.TieredItem<ItemData.WeaponData>>> rowMap = new HashMap<>();
        for (TierCalculator.TieredItem<ItemData.WeaponData> item : items) {
            rowMap.computeIfAbsent(item.row(), k -> new ArrayList<>()).add(item);
        }

        // Generate quests for each row
        for (int row = 0; row < AutoTierlistConfig.rowsPerTier; row++) {
            List<TierCalculator.TieredItem<ItemData.WeaponData>> rowItems = rowMap.get(row);
            if (rowItems == null || rowItems.isEmpty()) {
                continue;
            }

            double questY = QuestFactory.calculateQuestY(tierBaseY, row, AutoTierlistConfig.questSpacingY);
            generateRowQuests(questFile, chapter, rowItems, questY, columnAssignments);
        }
    }

    /**
     * Generate quests for a single row.
     */
    private void generateRowQuests(ServerQuestFile questFile, Chapter chapter,
                                   List<TierCalculator.TieredItem<ItemData.WeaponData>> items,
                                   double questY,
                                   Map<ResourceLocation, Integer> columnAssignments) {
        // Sort by column assignment if using progression, otherwise sequential
        if (!columnAssignments.isEmpty()) {
            // Sort by assigned column (items without assignments get -1 to appear first)
            items.sort(Comparator.comparing(item ->
                columnAssignments.getOrDefault(item.data().id(), -1)
            ));
        }

        // Track current X position for sequential placement
        double currentX = 0.0;
        int nextAutoColumn = 0;

        for (TierCalculator.TieredItem<ItemData.WeaponData> item : items) {
            double questX;

            if (columnAssignments.containsKey(item.data().id())) {
                // Use assigned column
                int column = columnAssignments.get(item.data().id());
                questX = column * AutoTierlistConfig.questSpacingX;
                nextAutoColumn = Math.max(nextAutoColumn, column + 1);
            } else {
                // Use sequential placement
                questX = nextAutoColumn * AutoTierlistConfig.questSpacingX;
                nextAutoColumn++;
            }

            // Create quest and track it
            Quest quest = QuestFactory.createItemQuest(questFile, chapter, item.data().stack(), questX, questY);
            itemToQuestMap.put(item.data().id(), quest);
        }
    }

    /**
     * Create quest dependencies based on crafting relationships.
     * If item A is used to craft item B, then quest B depends on quest A.
     */
    private void createQuestDependencies(Map<ResourceLocation, Set<ResourceLocation>> recipeGraph) {
        int dependenciesCreated = 0;

        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : recipeGraph.entrySet()) {
            ResourceLocation outputItem = entry.getKey();
            Set<ResourceLocation> ingredientItems = entry.getValue();

            Quest outputQuest = itemToQuestMap.get(outputItem);
            if (outputQuest == null) {
                continue; // Output item doesn't have a quest (shouldn't happen)
            }

            // Add dependencies for all ingredients that have quests
            for (ResourceLocation ingredientItem : ingredientItems) {
                Quest ingredientQuest = itemToQuestMap.get(ingredientItem);
                if (ingredientQuest != null) {
                    try {
                        outputQuest.addDependency(ingredientQuest);
                        dependenciesCreated++;
                        LOGGER.debug("Added dependency: {} -> {}", ingredientItem, outputItem);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to add dependency {} -> {}: {}",
                                  ingredientItem, outputItem, e.getMessage());
                    }
                }
            }
        }

        LOGGER.info("Created {} quest dependencies based on crafting relationships", dependenciesCreated);
    }

    /**
     * Assign column numbers for progression mode.
     * Items with no dependencies go left (sequential).
     * Items with dependencies are sorted by the minimum tier in their chain.
     */
    private Map<ResourceLocation, Integer> assignProgressionColumns(
            List<ResourceLocation> items,
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
            Map<ResourceLocation, Integer> tierMap) {

        Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();

        // Find items that have dependencies (appear as outputs in recipe graph)
        Set<ResourceLocation> itemsWithDependencies = new HashSet<>();
        for (ResourceLocation item : items) {
            if (recipeGraph.containsKey(item)) {
                Set<ResourceLocation> ingredients = recipeGraph.get(item);
                // Check if any ingredients are in our item list
                for (ResourceLocation ingredient : ingredients) {
                    if (items.contains(ingredient)) {
                        itemsWithDependencies.add(item);
                        break;
                    }
                }
            }
        }

        // Group items into chains using Union-Find
        CraftingChainDetector.UnionFind uf = new CraftingChainDetector.UnionFind(items);
        for (ResourceLocation output : recipeGraph.keySet()) {
            Set<ResourceLocation> ingredients = recipeGraph.get(output);
            for (ResourceLocation ingredient : ingredients) {
                if (items.contains(ingredient) && items.contains(output)) {
                    uf.union(output, ingredient);
                }
            }
        }

        // Get groups and calculate minimum tier for each
        List<Set<ResourceLocation>> groups = uf.getGroups();
        List<ChainGroup> chainGroups = new ArrayList<>();

        for (Set<ResourceLocation> group : groups) {
            // Check if this group has any dependencies
            boolean hasDependencies = false;
            for (ResourceLocation item : group) {
                if (itemsWithDependencies.contains(item)) {
                    hasDependencies = true;
                    break;
                }
            }

            // Find minimum tier in this group
            int minTier = Integer.MAX_VALUE;
            for (ResourceLocation item : group) {
                Integer tier = tierMap.get(item);
                if (tier != null && tier < minTier) {
                    minTier = tier;
                }
            }

            chainGroups.add(new ChainGroup(group, minTier, hasDependencies));
        }

        // Separate groups into no-deps and with-deps
        List<ChainGroup> noDepsGroups = new ArrayList<>();
        List<ChainGroup> withDepsGroups = new ArrayList<>();

        for (ChainGroup group : chainGroups) {
            if (group.hasDependencies) {
                withDepsGroups.add(group);
            } else {
                noDepsGroups.add(group);
            }
        }

        // Sort chains with dependencies by minimum tier
        withDepsGroups.sort(Comparator.comparingInt(g -> g.minTier));

        // Don't assign columns to no-dependency items - they'll use sequential placement
        // Count how many no-dep items we have for column offset calculation
        int noDepsCount = 0;
        for (ChainGroup group : noDepsGroups) {
            noDepsCount += group.items.size();
        }

        // Assign chains with dependencies to columns after the sequential no-deps area
        // We'll use a large offset to ensure chains are placed to the right
        int currentColumn = noDepsCount; // Use at least column 20 to leave space
        for (ChainGroup group : withDepsGroups) {
            for (ResourceLocation item : group.items) {
                columnAssignments.put(item, currentColumn);
            }
            currentColumn++;
        }

        LOGGER.info("Created {} chain groups (no-deps: {}, with-deps: {})",
            chainGroups.size(),
            chainGroups.stream().filter(g -> !g.hasDependencies).count(),
            chainGroups.stream().filter(g -> g.hasDependencies).count());

        return columnAssignments;
    }

    /**
     * Helper class for grouping items into chains.
     */
    private static class ChainGroup {
        final Set<ResourceLocation> items;
        final int minTier;
        final boolean hasDependencies;

        ChainGroup(Set<ResourceLocation> items, int minTier, boolean hasDependencies) {
            this.items = items;
            this.minTier = minTier;
            this.hasDependencies = hasDependencies;
        }
    }
}
