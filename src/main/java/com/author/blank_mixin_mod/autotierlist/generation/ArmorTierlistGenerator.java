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
 * Generates the armor tierlist chapter in FTBQuests.
 */
public class ArmorTierlistGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHAPTER_ID = "autotierlist_armor";

    private final TierOverrideManager overrideManager;
    private final Map<ResourceLocation, Quest> itemToQuestMap = new HashMap<>();

    public ArmorTierlistGenerator(TierOverrideManager overrideManager) {
        this.overrideManager = overrideManager;
    }

    /**
     * Generate the armor tierlist chapter.
     */
    public void generate(ServerQuestFile questFile, ServerLevel level) {
        LOGGER.info("Generating armor tierlist...");

        try {
            // 1. Create and configure item filter
            ItemFilter filter = new ItemFilter(AutoTierlistConfig.useAttributeDetection);
            filter.loadArmorTags(AutoTierlistConfig.ARMOR_TAGS.get());
            filter.loadArmorItems(AutoTierlistConfig.ARMOR_ITEMS.get());
            LOGGER.info("Item filter configured: {}", filter.getStats());

            // 2. Scan for armor
            ItemScanner scanner = new ItemScanner(filter);
            List<ItemData.ArmorData> armors = scanner.scanArmor();

            if (armors.isEmpty()) {
                LOGGER.warn("No armor found, skipping armor tierlist generation");
                return;
            }

            // 3. Calculate tier assignments
            TierCalculator calculator = new TierCalculator(
                AutoTierlistConfig.tierMultiplier,
                AutoTierlistConfig.rowsPerTier,
                overrideManager
            );
            Map<Integer, List<TierCalculator.TieredItem<ItemData.ArmorData>>> tiers =
                calculator.assignArmorTiers(armors);

            // 4. Detect progression chains if enabled
            Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
            if (AutoTierlistConfig.enableProgressionAlignment) {
                try {
                    CraftingChainDetector detector = new CraftingChainDetector(level.getRecipeManager());
                    List<ResourceLocation> armorIds = armors.stream()
                        .map(ItemData.ArmorData::id)
                        .collect(Collectors.toList());
                    recipeGraph = detector.getRecipeGraph(armorIds);

                    // Build tier map for sorting chains
                    Map<ResourceLocation, Integer> tierMap = new HashMap<>();
                    for (Map.Entry<Integer, List<TierCalculator.TieredItem<ItemData.ArmorData>>> entry : tiers.entrySet()) {
                        for (TierCalculator.TieredItem<ItemData.ArmorData> item : entry.getValue()) {
                            tierMap.put(item.data().id(), item.tier());
                        }
                    }

                    columnAssignments = assignProgressionColumns(armorIds, recipeGraph, tierMap);
                    LOGGER.info("Assigned {} armor pieces to progression columns", columnAssignments.size());
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
            chapter.setRawTitle("Armor Tierlist");
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

            LOGGER.info("Armor tierlist generated successfully with {} tiers", tiers.size());

        } catch (Exception e) {
            LOGGER.error("Failed to generate armor tierlist", e);
            throw new RuntimeException("Armor tierlist generation failed", e);
        }
    }

    /**
     * Generate quests for a single tier.
     *
     * @param tier The tier number (for labeling)
     * @param tierIndex The sequential index (for positioning)
     */
    private void generateTierQuests(ServerQuestFile questFile, Chapter chapter, int tier, int tierIndex,
                                    List<TierCalculator.TieredItem<ItemData.ArmorData>> items,
                                    Map<ResourceLocation, Integer> columnAssignments) {
        // Calculate tier base Y position using sequential index, not tier number
        double tierBaseY = QuestFactory.calculateTierBaseY(
            tierIndex,
            AutoTierlistConfig.rowsPerTier,
            AutoTierlistConfig.questSpacingY,
            AutoTierlistConfig.tierSpacingY
        );

        // Create secret tier marker quest
        QuestFactory.createSecretTierQuest(questFile, chapter, tier, tierBaseY);

        // Group items by row
        Map<Integer, List<TierCalculator.TieredItem<ItemData.ArmorData>>> rowMap = new HashMap<>();
        for (TierCalculator.TieredItem<ItemData.ArmorData> item : items) {
            rowMap.computeIfAbsent(item.row(), k -> new ArrayList<>()).add(item);
        }

        // Generate quests for each row
        for (int row = 0; row < AutoTierlistConfig.rowsPerTier; row++) {
            List<TierCalculator.TieredItem<ItemData.ArmorData>> rowItems = rowMap.get(row);
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
                                   List<TierCalculator.TieredItem<ItemData.ArmorData>> items,
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

        for (TierCalculator.TieredItem<ItemData.ArmorData> item : items) {
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
     * Items with dependencies form vertical chains with their exclusive dependencies.
     */
    private Map<ResourceLocation, Integer> assignProgressionColumns(
            List<ResourceLocation> items,
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
            Map<ResourceLocation, Integer> tierMap) {

        Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();
        Set<ResourceLocation> itemSet = new HashSet<>(items);

        // Build reverse graph: ingredient -> outputs that use it
        Map<ResourceLocation, Set<ResourceLocation>> reverseGraph = new HashMap<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : recipeGraph.entrySet()) {
            ResourceLocation output = entry.getKey();
            for (ResourceLocation ingredient : entry.getValue()) {
                if (itemSet.contains(ingredient)) {
                    reverseGraph.computeIfAbsent(ingredient, k -> new HashSet<>()).add(output);
                }
            }
        }

        // Find items that have dependencies
        Set<ResourceLocation> itemsWithDependencies = new HashSet<>();
        for (ResourceLocation item : items) {
            if (recipeGraph.containsKey(item)) {
                Set<ResourceLocation> ingredients = recipeGraph.get(item);
                if (ingredients.stream().anyMatch(itemSet::contains)) {
                    itemsWithDependencies.add(item);
                }
            }
        }

        // Track which dependencies have been "claimed" by an output for column sharing
        Set<ResourceLocation> claimedDependencies = new HashSet<>();

        // Start assigning columns after no-dependency items
        int nextColumn = items.size();

        // Sort items with dependencies by tier for consistent ordering
        List<ResourceLocation> withDeps = new ArrayList<>(itemsWithDependencies);
        withDeps.sort(Comparator.comparing(item -> tierMap.getOrDefault(item, Integer.MAX_VALUE)));

        for (ResourceLocation item : withDeps) {
            Set<ResourceLocation> ingredients = recipeGraph.getOrDefault(item, Collections.emptySet());

            // Try to find an unclaimed dependency to share a column with
            ResourceLocation chosenDependency = null;
            for (ResourceLocation ingredient : ingredients) {
                if (!itemSet.contains(ingredient)) continue;
                if (claimedDependencies.contains(ingredient)) continue;

                // Prefer exclusive dependencies (used only by this item)
                Set<ResourceLocation> usedBy = reverseGraph.getOrDefault(ingredient, Collections.emptySet());
                if (usedBy.size() == 1) {
                    chosenDependency = ingredient;
                    claimedDependencies.add(ingredient);
                    break;
                }
            }

            // If no exclusive dependency, try to find any unclaimed dependency
            if (chosenDependency == null) {
                for (ResourceLocation ingredient : ingredients) {
                    if (!itemSet.contains(ingredient)) continue;
                    if (claimedDependencies.contains(ingredient)) continue;

                    chosenDependency = ingredient;
                    claimedDependencies.add(ingredient);
                    break;
                }
            }

            if (chosenDependency != null) {
                // Share column with the chosen dependency
                if (columnAssignments.containsKey(chosenDependency)) {
                    // Dependency already has a column, use it
                    columnAssignments.put(item, columnAssignments.get(chosenDependency));
                } else {
                    // Assign both to a new column (vertical chain)
                    int column = nextColumn++;
                    columnAssignments.put(chosenDependency, column);
                    columnAssignments.put(item, column);
                }
            } else {
                // All dependencies are claimed, assign new column
                columnAssignments.put(item, nextColumn++);
            }
        }

        LOGGER.info("Assigned {} items to progression columns (no-deps: {}, with-deps: {})",
            columnAssignments.size(),
            items.size() - itemsWithDependencies.size(),
            itemsWithDependencies.size());

        return columnAssignments;
    }

}
