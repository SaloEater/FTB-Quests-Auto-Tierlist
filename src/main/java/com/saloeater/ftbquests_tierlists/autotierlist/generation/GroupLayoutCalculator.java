package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.mojang.logging.LogUtils;
import com.saloeater.ftbquests_tierlists.Tierlists;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 2: Assigns column positions to items within each group.
 * Handles spacing between groups to prevent visual overlap.
 *
 * @param <T> The item data type (weapon or armor)
 */
public class GroupLayoutCalculator<T> {
    public static final int PROGRESSION_SPACING = 1;
    public static final int TIER_SPACING = 2;

    private final Function<T, ResourceLocation> getItemId;
    private final Function<T, Double> getItemScore;

    public GroupLayoutCalculator(Function<T, ResourceLocation> getItemId,
                                Function<T, Double> getItemScore) {
        this.getItemId = getItemId;
        this.getItemScore = getItemScore;
    }

    /**
     * Calculate column assignments for all groups.
     * Assigns contiguous column ranges to each group with spacing between them.
     *
     * @param groups The item groups
     * @param recipeGraph Recipe graph for progression chains
     * @param tierMap Tier assignments for each item
     * @param scoreMap Score values for each item
     */
    public void calculateLayout(List<ItemGroup<T>> groups,
                                Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
                                Map<ResourceLocation, Integer> tierMap,
                                Map<ResourceLocation, Double> scoreMap,
                                int groupSpacing) {

        int nextStartColumn = 0;

        for (ItemGroup<T> group : groups) {
            if (group.isEmpty()) continue;

            switch (group.getType()) {
                case PROGRESSION_CHAIN:
                    assignProgressionChainColumns(group, recipeGraph, tierMap, scoreMap, nextStartColumn);
                    break;

                case TAG_GROUP:
                case ISOLATED:
                    assignSequentialColumns(group, tierMap, nextStartColumn);
                    break;
            }

            // Find the maximum column used in this group
            int maxColumn = group.getColumnAssignments().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(nextStartColumn - 1);

            // Next group starts after this one with spacing
            nextStartColumn = maxColumn + 1 + groupSpacing;
        }

        Tierlists.LOGGER.info("Calculated layout for {} groups", groups.size());
    }

    /**
     * Assign columns for a progression chain using dependency-based alignment.
     * Delegates to ProgressionHelper for the complex column assignment logic.
     */
    private void assignProgressionChainColumns(ItemGroup<T> group,
                                              Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
                                              Map<ResourceLocation, Integer> tierMap,
                                              Map<ResourceLocation, Double> scoreMap,
                                              int startColumn) {

        Set<ResourceLocation> chainItemIds = group.getChainItemIds();
        if (chainItemIds == null) return;

        List<ResourceLocation> itemIds = group.getItems().stream()
            .map(getItemId)
            .collect(Collectors.toList());

        // Use ProgressionHelper to assign columns within this chain
        Map<ResourceLocation, Integer> relativeColumns = ProgressionHelper.assignProgressionColumns(
            itemIds, recipeGraph, tierMap, scoreMap
        );

        // Offset all columns to start at the group's start column
        if (!relativeColumns.isEmpty()) {
            int minColumn = relativeColumns.values().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

            int offset = startColumn - minColumn;

            for (Map.Entry<ResourceLocation, Integer> entry : relativeColumns.entrySet()) {
                int absoluteColumn = entry.getValue() + offset;
                group.setColumnAssignment(entry.getKey(), absoluteColumn);
            }
        }
    }

    /**
     * Assign sequential columns for tag groups and isolated items.
     * Items are sorted by tier (ascending), then by mod ID, then by score (ascending - weaker first).
     */
    private void assignSequentialColumns(ItemGroup<T> group,
                                        Map<ResourceLocation, Integer> tierMap,
                                        int startColumn) {

        // Sort items by tier, then by mod ID (namespace), then by score (weaker first)
        List<T> sortedItems = new ArrayList<>(group.getItems());
        sortedItems.sort(Comparator
            .comparing((T item) -> tierMap.getOrDefault(getItemId.apply(item), Integer.MAX_VALUE))
            .thenComparing((T item) -> getItemId.apply(item).getNamespace())
            .thenComparing(getItemScore));

        // Group items by tier to assign columns within each tier
        Map<Integer, List<T>> itemsByTier = new HashMap<>();
        for (T item : sortedItems) {
            int tier = tierMap.getOrDefault(getItemId.apply(item), 0);
            itemsByTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(item);
        }

        // Find the maximum number of items in any tier (this determines the width of the group)
        int maxItemsInTier = itemsByTier.values().stream()
            .mapToInt(List::size)
            .max()
            .orElse(0);

        // Assign columns sequentially within each tier
        for (Map.Entry<Integer, List<T>> entry : itemsByTier.entrySet()) {
            List<T> tierItems = entry.getValue();

            for (int i = 0; i < tierItems.size(); i++) {
                T item = tierItems.get(i);
                ResourceLocation itemId = getItemId.apply(item);
                int column = startColumn + i;
                group.setColumnAssignment(itemId, column);
            }
        }
    }
}
