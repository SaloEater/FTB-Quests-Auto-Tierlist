package com.author.blank_mixin_mod.autotierlist.generation;

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
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract base class for tierlist generators (weapon and armor).
 * Contains all common logic with abstract methods for type-specific operations.
 *
 * @param <T> The item data type (WeaponData or ArmorData)
 */
public abstract class AbstractTierlistGenerator<T> {
    protected static final Logger LOGGER = LogUtils.getLogger();

    protected final TierOverrideManager overrideManager;
    protected final Map<ResourceLocation, Quest> itemToQuestMap = new HashMap<>();

    public AbstractTierlistGenerator(TierOverrideManager overrideManager) {
        this.overrideManager = overrideManager;
    }

    /**
     * Generate the tierlist chapter.
     */
    public void generate(ServerQuestFile questFile, ServerLevel level) {
        LOGGER.info("Generating {} tierlist...", getItemTypeName());

        try {
            // 1. Create and configure item filter
            ItemFilter filter = new ItemFilter(AutoTierlistConfig.useAttributeDetection);
            configureFilter(filter);
            LOGGER.info("Item filter configured: {}", filter.getStats());

            // 2. Scan for items
            ItemScanner scanner = new ItemScanner(filter);
            List<T> items = scanItems(scanner);

            if (items.isEmpty()) {
                LOGGER.warn("No {} found, skipping tierlist generation", getItemTypeName());
                return;
            }

            // 3. Calculate tier assignments
            TierCalculator calculator = new TierCalculator(
                AutoTierlistConfig.tierMultiplier,
                AutoTierlistConfig.rowsPerTier,
                overrideManager
            );
            Map<Integer, List<TierCalculator.TieredItem<T>>> tiers = assignTiers(calculator, items);

            // 4. Detect progression chains if enabled
            Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
            if (AutoTierlistConfig.enableProgressionAlignment) {
                try {
                    CraftingChainDetector detector = new CraftingChainDetector(level.getRecipeManager());
                    List<ResourceLocation> itemIds = items.stream()
                        .map(this::getItemId)
                        .collect(Collectors.toList());
                    recipeGraph = detector.getRecipeGraph(itemIds);

                    // Build tier map and score map for sorting chains
                    Map<ResourceLocation, Integer> tierMap = new HashMap<>();
                    Map<ResourceLocation, Double> scoreMap = new HashMap<>();
                    for (Map.Entry<Integer, List<TierCalculator.TieredItem<T>>> entry : tiers.entrySet()) {
                        for (TierCalculator.TieredItem<T> item : entry.getValue()) {
                            ResourceLocation id = getItemId(item.data());
                            tierMap.put(id, item.tier());
                            scoreMap.put(id, getItemScore(item.data()));
                        }
                    }

                    columnAssignments = ProgressionHelper.assignProgressionColumns(itemIds, recipeGraph, tierMap, scoreMap);
                    LOGGER.info("Assigned {} {} to progression columns", columnAssignments.size(), getItemTypeName());
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

            Chapter chapter = new Chapter(questFile.newID(), questFile, defaultGroup, getChapterId());
            chapter.setRawTitle(getChapterTitle());
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

            LOGGER.info("{} tierlist generated successfully with {} tiers", getItemTypeName(), tiers.size());

        } catch (Exception e) {
            LOGGER.error("Failed to generate {} tierlist", getItemTypeName(), e);
            throw new RuntimeException(getItemTypeName() + " tierlist generation failed", e);
        }
    }

    /**
     * Generate quests for a single tier.
     */
    private void generateTierQuests(ServerQuestFile questFile, Chapter chapter, int tier, int tierIndex,
                                    List<TierCalculator.TieredItem<T>> items,
                                    Map<ResourceLocation, Integer> columnAssignments) {
        // Calculate tier base Y position using sequential index, not tier number
        double tierBaseY = QuestFactory.calculateTierBaseY(
            tierIndex,
            AutoTierlistConfig.rowsPerTier,
            AutoTierlistConfig.questSpacingY,
            AutoTierlistConfig.tierSpacingY
        );

        // Create secret tier marker quest
        String tierLabel = getTierLabel(tier);
        QuestFactory.createSecretTierQuest(questFile, chapter, tierLabel, tierBaseY);

        // Group items by row
        Map<Integer, List<TierCalculator.TieredItem<T>>> rowMap = new HashMap<>();
        for (TierCalculator.TieredItem<T> item : items) {
            rowMap.computeIfAbsent(item.row(), k -> new ArrayList<>()).add(item);
        }

        // Generate quests for each row
        for (int row = 0; row < AutoTierlistConfig.rowsPerTier; row++) {
            List<TierCalculator.TieredItem<T>> rowItems = rowMap.get(row);
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
                                   List<TierCalculator.TieredItem<T>> items,
                                   double questY,
                                   Map<ResourceLocation, Integer> columnAssignments) {
        // Sort items: first by column assignment, then by score (weaker first)
        if (!columnAssignments.isEmpty()) {
            // Sort by assigned column (items without assignments get -1 to appear first),
            // then by score (ascending - weaker items first)
            items.sort(Comparator.comparing((TierCalculator.TieredItem<T> item) ->
                    columnAssignments.getOrDefault(getItemId(item.data()), -1))
                .thenComparing(item -> getItemScore(item.data())));
        } else {
            // No progression mode - sort all items by score (weaker first)
            items.sort(Comparator.comparing(item -> getItemScore(item.data())));
        }

        // Track current X position for sequential placement
        int nextAutoColumn = 0;

        for (TierCalculator.TieredItem<T> item : items) {
            double questX;
            ResourceLocation itemId = getItemId(item.data());

            if (columnAssignments.containsKey(itemId)) {
                // Use assigned column
                int column = columnAssignments.get(itemId);
                questX = column * AutoTierlistConfig.questSpacingX;
                nextAutoColumn = Math.max(nextAutoColumn, column + 1);
            } else {
                // Use sequential placement
                questX = nextAutoColumn * AutoTierlistConfig.questSpacingX;
                nextAutoColumn++;
            }

            // Create quest and track it
            Quest quest = QuestFactory.createItemQuest(questFile, chapter, getItemStack(item.data()), questX, questY);
            itemToQuestMap.put(itemId, quest);
        }
    }

    /**
     * Create quest dependencies based on crafting relationships.
     * Uses topological ordering to avoid circular dependencies.
     */
    private void createQuestDependencies(Map<ResourceLocation, Set<ResourceLocation>> recipeGraph) {
        int dependenciesCreated = 0;
        int skippedCycles = 0;

        // Build a set to track created dependencies for cycle detection
        Map<Quest, Set<Quest>> questDependencies = new HashMap<>();

        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry : recipeGraph.entrySet()) {
            ResourceLocation outputItem = entry.getKey();
            Set<ResourceLocation> ingredientItems = entry.getValue();

            Quest outputQuest = itemToQuestMap.get(outputItem);
            if (outputQuest == null) {
                continue;
            }

            for (ResourceLocation ingredientItem : ingredientItems) {
                Quest ingredientQuest = itemToQuestMap.get(ingredientItem);
                if (ingredientQuest != null) {
                    // Check if adding this dependency would create a cycle
                    List<ResourceLocation> cyclePath = findCyclePath(outputQuest, ingredientQuest, questDependencies);
                    if (cyclePath != null) {
                        String cycleStr = String.join(" -> ", cyclePath.stream().map(ResourceLocation::toString).toList());
                        LOGGER.warn("Skipping dependency {} -> {} to avoid circular dependency. Cycle: {} -> {}",
                                  ingredientItem, outputItem, cycleStr, ingredientItem);
                        skippedCycles++;
                        continue;
                    }

                    try {
                        outputQuest.addDependency(ingredientQuest);
                        questDependencies.computeIfAbsent(outputQuest, k -> new HashSet<>()).add(ingredientQuest);
                        dependenciesCreated++;
                        LOGGER.debug("Added dependency: {} -> {}", ingredientItem, outputItem);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to add dependency {} -> {}: {}",
                                  ingredientItem, outputItem, e.getMessage());
                    }
                }
            }
        }

        LOGGER.info("Created {} quest dependencies ({} skipped to avoid cycles)",
                   dependenciesCreated, skippedCycles);
    }

    /**
     * Find the cycle path if adding a dependency would create a cycle.
     * Returns the path of item IDs that form the cycle, or null if no cycle would be created.
     *
     * @param dependent The quest that would depend on dependency
     * @param dependency The quest that would be depended upon
     * @param questDependencies Current quest dependency graph
     * @return List of item IDs forming the cycle, or null if no cycle
     */
    private List<ResourceLocation> findCyclePath(Quest dependent, Quest dependency,
                                                Map<Quest, Set<Quest>> questDependencies) {
        // Build reverse lookup: Quest -> ItemId
        Map<Quest, ResourceLocation> questToItem = new HashMap<>();
        for (Map.Entry<ResourceLocation, Quest> entry : itemToQuestMap.entrySet()) {
            questToItem.put(entry.getValue(), entry.getKey());
        }

        // If dependency already depends on dependent (directly or indirectly), adding this would create a cycle
        List<Quest> cyclePath = findDependencyPath(dependency, dependent, questDependencies, new ArrayList<>(), new HashSet<>());

        if (cyclePath != null) {
            // Convert quest path to item ID path
            List<ResourceLocation> itemPath = new ArrayList<>();
            for (Quest quest : cyclePath) {
                ResourceLocation itemId = questToItem.get(quest);
                if (itemId != null) {
                    itemPath.add(itemId);
                }
            }
            return itemPath;
        }

        return null;
    }

    /**
     * Find a dependency path from 'from' to 'to' using DFS.
     * Returns the path as a list of quests, or null if no path exists.
     */
    private List<Quest> findDependencyPath(Quest from, Quest to,
                                          Map<Quest, Set<Quest>> questDependencies,
                                          List<Quest> currentPath,
                                          Set<Quest> visited) {
        if (from.equals(to)) {
            currentPath.add(from);
            return new ArrayList<>(currentPath);
        }

        if (visited.contains(from)) {
            return null; // Already checked this path
        }

        visited.add(from);
        currentPath.add(from);

        Set<Quest> deps = questDependencies.get(from);
        if (deps != null) {
            for (Quest dep : deps) {
                List<Quest> result = findDependencyPath(dep, to, questDependencies, currentPath, visited);
                if (result != null) {
                    return result;
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        return null;
    }

    // Abstract methods that subclasses must implement

    /**
     * Get the chapter ID for this tierlist type.
     */
    protected abstract String getChapterId();

    /**
     * Get the chapter title for this tierlist type.
     */
    protected abstract String getChapterTitle();

    /**
     * Get the item type name for logging (e.g., "weapons" or "armor").
     */
    protected abstract String getItemTypeName();

    /**
     * Configure the item filter with tags and items.
     */
    protected abstract void configureFilter(ItemFilter filter);

    /**
     * Scan for items using the scanner.
     */
    protected abstract List<T> scanItems(ItemScanner scanner);

    /**
     * Assign tiers to items using the calculator.
     */
    protected abstract Map<Integer, List<TierCalculator.TieredItem<T>>> assignTiers(TierCalculator calculator, List<T> items);

    /**
     * Get the ResourceLocation ID from an item.
     */
    protected abstract ResourceLocation getItemId(T item);

    /**
     * Get the ItemStack from an item.
     */
    protected abstract ItemStack getItemStack(T item);

    /**
     * Get the tier label for displaying on the secret quest.
     */
    protected abstract String getTierLabel(int tier);

    /**
     * Get the numeric score for an item (DPS for weapons, armor score for armor).
     * Used for sorting items within the same tier in progression mode.
     */
    protected abstract double getItemScore(T item);
}
