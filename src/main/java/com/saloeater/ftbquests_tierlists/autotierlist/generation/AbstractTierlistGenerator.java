package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemScanner;
import com.saloeater.ftbquests_tierlists.autotierlist.analysis.TierCalculator;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter;
import com.saloeater.ftbquests_tierlists.autotierlist.config.TierOverrideManager;
import com.saloeater.ftbquests_tierlists.autotierlist.progression.CraftingChainDetector;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.*;
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
     * Generate the tierlist chapter using the 3-phase pipeline.
     *
     * @param questFile The quest file
     * @param level The server level
     * @param enableProgressionAlignment Whether to enable progression-based alignment
     */
    public void generate(ServerQuestFile questFile, ServerLevel level, boolean enableProgressionAlignment) {
        LOGGER.info("Generating {} tierlist (progression: {})...", getItemTypeName(), enableProgressionAlignment);

        try {
            // 1. Create and configure item filter
            ItemFilter filter = new ItemFilter(AutoTierlistConfig.USE_ATTRIBUTE_DETECTION.get());
            filter.loadSkippedItems(AutoTierlistConfig.SKIPPED_ITEMS.get());
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
                AutoTierlistConfig.TIER_MULTIPLIER,
                AutoTierlistConfig.ROWS_PER_TIER,
                overrideManager
            );
            Map<Integer, List<TierCalculator.TieredItem<T>>> tiers = assignTiers(calculator, items);

            // Flatten tiered items into a single list
            List<TierCalculator.TieredItem<T>> allTieredItems = new ArrayList<>();
            for (List<TierCalculator.TieredItem<T>> tierItems : tiers.values()) {
                allTieredItems.addAll(tierItems);
            }

            // Build tier map and score map for grouping and layout
            Map<ResourceLocation, Integer> tierMap = new HashMap<>();
            Map<ResourceLocation, Double> scoreMap = new HashMap<>();
            for (TierCalculator.TieredItem<T> item : allTieredItems) {
                ResourceLocation id = getItemId(item.data());
                tierMap.put(id, item.tier());
                scoreMap.put(id, getItemScore(item.data()));
            }

            // 4. Detect progression chains if enabled
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
            if (enableProgressionAlignment) {
                try {
                    CraftingChainDetector detector = new CraftingChainDetector(level.getRecipeManager());
                    List<ResourceLocation> itemIds = items.stream()
                        .map(this::getItemId)
                        .collect(Collectors.toList());
                    recipeGraph = detector.getRecipeGraph(itemIds);
                    LOGGER.info("Detected {} recipe relationships", recipeGraph.size());
                } catch (Exception e) {
                    LOGGER.error("Failed to detect progression chains, continuing without progression alignment", e);
                }
            }

            // === PHASE 1: Build item groups ===
            ItemGroupBuilder<T> groupBuilder = new ItemGroupBuilder<>(
                this::getItemId,
                this::getItemStack,
                this::getItemScore
            );
            List<ItemGroup<T>> groups = groupBuilder.buildGroups(allTieredItems, recipeGraph, tierMap, enableProgressionAlignment);

            // === PHASE 2: Calculate layout for groups ===
            GroupLayoutCalculator<T> layoutCalculator = new GroupLayoutCalculator<>(
                this::getItemId,
                this::getItemScore
            );
            int groupSpacing = enableProgressionAlignment ? GroupLayoutCalculator.PROGRESSION_SPACING : GroupLayoutCalculator.TIER_SPACING;
            layoutCalculator.calculateLayout(groups, recipeGraph, tierMap, scoreMap, groupSpacing);

            // Build global column assignments from all groups
            Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();
            for (ItemGroup<T> group : groups) {
                columnAssignments.putAll(group.getColumnAssignments());
            }

            // === PHASE 3: Generate quests ===
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

            // 6.5. Create header quests for tag groups (non-progression mode only)
            if (!enableProgressionAlignment) {
                createTagGroupHeaders(questFile, chapter, groups, columnAssignments, tierMap);
            }

            // 7. Create quest dependencies based on crafting relationships
            if (enableProgressionAlignment && !recipeGraph.isEmpty()) {
                createQuestDependencies(recipeGraph);
            }

            LOGGER.info("{} tierlist generated successfully with {} tiers and {} groups",
                getItemTypeName(), tiers.size(), groups.size());

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
            AutoTierlistConfig.ROWS_PER_TIER,
            AutoTierlistConfig.QUEST_SPACING_Y.get(),
            AutoTierlistConfig.TIER_SPACING_Y.get()
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
        for (int row = 0; row < AutoTierlistConfig.ROWS_PER_TIER; row++) {
            List<TierCalculator.TieredItem<T>> rowItems = rowMap.get(row);
            if (rowItems == null || rowItems.isEmpty()) {
                continue;
            }

            double questY = QuestFactory.calculateQuestY(tierBaseY, row, AutoTierlistConfig.QUEST_SPACING_Y.get());
            // Calculate global row number for alternating colors (tierIndex * rowsPerTier + row)
            int globalRowNumber = tierIndex * AutoTierlistConfig.ROWS_PER_TIER + row;
            generateRowQuests(questFile, chapter, rowItems, questY, columnAssignments, globalRowNumber);
        }
    }

    /**
     * Generate quests for a single row.
     */
    private void generateRowQuests(ServerQuestFile questFile, Chapter chapter,
                                   List<TierCalculator.TieredItem<T>> items,
                                   double questY,
                                   Map<ResourceLocation, Integer> columnAssignments,
                                   int rowNumber) {
        // Sort items: first by column assignment, then by Armageddon tag order, then by score (weaker first)
        if (!columnAssignments.isEmpty()) {
            // Sort by assigned column (items without assignments get -1 to appear first),
            // then by Armageddon tag order (items with no tags = -1 to appear first),
            // then by score (ascending - weaker items first)
            items.sort(Comparator.comparing((TierCalculator.TieredItem<T> item) ->
                    columnAssignments.getOrDefault(getItemId(item.data()), -1))
                .thenComparing(item -> getArmageddonTagIndex(getItemStack(item.data())))
                .thenComparing(item -> getItemScore(item.data())));
        } else {
            // No progression mode - sort by Armageddon tag order, then by score (weaker first)
            items.sort(Comparator.comparing((TierCalculator.TieredItem<T> item) ->
                    getArmageddonTagIndex(getItemStack(item.data())))
                .thenComparing(item -> getItemScore(item.data())));
        }

        // Track current X position for sequential placement
        int nextAutoColumn = 0;

        for (TierCalculator.TieredItem<T> item : items) {
            double questX;
            ResourceLocation itemId = getItemId(item.data());

            if (columnAssignments.containsKey(itemId)) {
                // Use assigned column
                int column = columnAssignments.get(itemId);
                questX = column * AutoTierlistConfig.QUEST_SPACING_X.get();
                nextAutoColumn = Math.max(nextAutoColumn, column + 1);
            } else {
                // Use sequential placement
                questX = nextAutoColumn * AutoTierlistConfig.QUEST_SPACING_X.get();
                nextAutoColumn++;
            }

            // Create quest and track it
            Quest quest = QuestFactory.createItemQuest(questFile, chapter, getItemStack(item.data()), questX, questY);
            itemToQuestMap.put(itemId, quest);
        }

        // Create background image for the row
        // Alternate between light and dark gray based on row number
        String imagePath = (rowNumber % 2 == 0)
            ? "ftblibrary:textures/gui/row_light_gray.png"
            : "ftblibrary:textures/gui/row_dark_gray.png";

        ChapterImage image = new ChapterImage(chapter);
        image.setImage(Icon.getIcon(new ResourceLocation(imagePath)));

        ConfigGroup group = new ConfigGroup("");
        image.fillConfigGroup(group);

        // Calculate image dimensions
        // Start at the same X position as the secret tier quest (-2.0)
        double imageX = -2.0;
        double imageY = questY;
        double imageWidth = 1000.0;  // Fixed large width
        double imageHeight = 0.05;  // Very narrow vertical strip

        for (var value : group.getValues()) {
            if (value.id.equals("width")) {
                ConfigValue<Double> widthVal = (ConfigValue<Double>) value;
                widthVal.setCurrentValue(imageWidth);
                widthVal.applyValue();
            } else if (value.id.equals("height")) {
                ConfigValue<Double> heightVal = (ConfigValue<Double>) value;
                heightVal.setCurrentValue(imageHeight);
                heightVal.applyValue();
            } else if (value.id.equals("x")) {
                ConfigValue<Double> xVal = (ConfigValue<Double>) value;
                xVal.setCurrentValue(imageX);
                xVal.applyValue();
            } else if (value.id.equals("y")) {
                ConfigValue<Double> yVal = (ConfigValue<Double>) value;
                yVal.setCurrentValue(imageY);
                yVal.applyValue();
            }
        }
        chapter.addImage(image);
    }

    /**
     * Create header quests for tag groups above the global first row.
     */
    private void createTagGroupHeaders(ServerQuestFile questFile, Chapter chapter,
                                      List<ItemGroup<T>> groups,
                                      Map<ResourceLocation, Integer> columnAssignments,
                                      Map<ResourceLocation, Integer> tierMap) {
        // Position headers above the global first row (Y = 0)
        double headerY = -4.0;

        for (ItemGroup<T> group : groups) {
            if (group.getType() != GroupType.TAG_GROUP) continue;

            AutoTierlistConfig.TagEntry tagEntry = group.getTagEntry();
            if (tagEntry == null || !tagEntry.hasHeader()) continue;

            // Find the min and max columns used by this group
            Integer minColumn = null;
            Integer maxColumn = null;
            for (T item : group.getItems()) {
                ResourceLocation itemId = getItemId(item);
                Integer column = columnAssignments.get(itemId);
                if (column != null) {
                    if (minColumn == null || column < minColumn) {
                        minColumn = column;
                    }
                    if (maxColumn == null || column > maxColumn) {
                        maxColumn = column;
                    }
                }
            }

            if (minColumn == null || maxColumn == null) continue;

            // Calculate center X position of the group
            double centerColumn = (minColumn + maxColumn) / 2.0;
            double headerX = centerColumn * AutoTierlistConfig.QUEST_SPACING_X.get();

            // Create header quest with 3x3 size
            ResourceLocation headerItemId = new ResourceLocation(tagEntry.getHeaderItem());
            QuestFactory.createHeaderQuest(
                questFile,
                chapter,
                headerItemId,
                tagEntry.getHeaderTitle(),
                headerX,
                headerY
            );

            LOGGER.info("Created header quest for tag group '{}' at ({}, {}) (columns {}-{})",
                tagEntry.getLabel(), headerX, headerY, minColumn, maxColumn);
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

    /**
     * Get the Armageddon tag index for an item.
     * Returns -1 if the item has no Armageddon tags (will appear first).
     * Returns the index of the first matching tag in the config (0, 1, 2, etc.).
     * This allows sorting items by their tag priority from the config.
     */
    private int getArmageddonTagIndex(ItemStack stack) {
        List<AutoTierlistConfig.TagEntry> tagEntries = AutoTierlistConfig.getArmageddonTagEntries();

        // Check each tag entry in order (this is the priority order)
        for (int i = 0; i < tagEntries.size(); i++) {
            AutoTierlistConfig.TagEntry entry = tagEntries.get(i);

            // Check if the item has any of this entry's tags
            for (var tagKey : entry.getTagKeys()) {
                if (stack.is(tagKey)) {
                    return i; // Return the index of this tag entry
                }
            }
        }

        // No Armageddon tags found, return -1 so this item appears first
        return -1;
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
