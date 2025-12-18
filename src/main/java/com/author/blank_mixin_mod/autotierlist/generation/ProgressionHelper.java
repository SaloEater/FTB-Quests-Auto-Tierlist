package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.progression.CraftingChainDetector;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for progression mode column assignment and chain building.
 */
public class ProgressionHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Assign column numbers for progression mode.
     * Items with no dependencies and never used as dependencies go left (sequential).
     * Items that are dependencies or have dependencies get their own columns.
     * Each chain occupies a contiguous block of columns to prevent overlap.
     */
    public static Map<ResourceLocation, Integer> assignProgressionColumns(
            List<ResourceLocation> items,
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
            Map<ResourceLocation, Integer> tierMap,
            Map<ResourceLocation, Double> scoreMap) {

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

        // Find items that are used as dependencies
        Set<ResourceLocation> itemsUsedAsDependencies = new HashSet<>(reverseGraph.keySet());

        // Calculate items that will use sequential placement (no deps and never used as deps)
        Set<ResourceLocation> sequentialItems = new HashSet<>(items);
        sequentialItems.removeAll(itemsWithDependencies);
        sequentialItems.removeAll(itemsUsedAsDependencies);

        // Find the maximum number of sequential items in any single tier/row
        Map<Integer, Integer> sequentialCountByTier = new HashMap<>();
        for (ResourceLocation item : sequentialItems) {
            int tier = tierMap.getOrDefault(item, 0);
            sequentialCountByTier.put(tier, sequentialCountByTier.getOrDefault(tier, 0) + 1);
        }
        int maxSequentialInRow = sequentialCountByTier.values().stream()
            .max(Integer::compareTo)
            .orElse(0);

        // Build chains: groups of items connected by dependencies
        Map<ResourceLocation, Set<ResourceLocation>> chains = new HashMap<>();
        Set<ResourceLocation> processedItems = new HashSet<>();

        // Sort items with dependencies by tier for consistent ordering
        List<ResourceLocation> withDeps = new ArrayList<>(itemsWithDependencies);
        withDeps.sort(Comparator.comparing(item -> tierMap.getOrDefault(item, Integer.MAX_VALUE)));

        // Build chains by traversing dependency relationships
        for (ResourceLocation item : withDeps) {
            if (processedItems.contains(item)) continue;

            Set<ResourceLocation> chain = new HashSet<>();
            buildChain(item, chain, recipeGraph, reverseGraph, itemSet, itemsWithDependencies, itemsUsedAsDependencies);

            if (!chain.isEmpty()) {
                // Pick a representative for this chain (lowest tier item)
                ResourceLocation representative = chain.stream()
                    .min(Comparator.comparing(i -> tierMap.getOrDefault(i, Integer.MAX_VALUE)))
                    .orElse(item);
                chains.put(representative, chain);
                processedItems.addAll(chain);
            }
        }

        // Add remaining items used as dependencies
        for (ResourceLocation dep : itemsUsedAsDependencies) {
            if (!processedItems.contains(dep)) {
                Set<ResourceLocation> chain = new HashSet<>();
                chain.add(dep);
                chains.put(dep, chain);
                processedItems.add(dep);
            }
        }

        // Sort chains by their minimum tier
        List<Map.Entry<ResourceLocation, Set<ResourceLocation>>> sortedChains = new ArrayList<>(chains.entrySet());
        sortedChains.sort(Comparator.comparing(e ->
            e.getValue().stream()
                .mapToInt(i -> tierMap.getOrDefault(i, Integer.MAX_VALUE))
                .min()
                .orElse(Integer.MAX_VALUE)
        ));

        // Assign columns to each chain
        // Start after the widest row of sequential items
        int nextColumn = maxSequentialInRow;

        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> chainEntry : sortedChains) {
            Set<ResourceLocation> chain = chainEntry.getValue();
            int chainStartColumn = nextColumn;

            // Assign columns within this chain
            assignChainColumns(chain, columnAssignments, recipeGraph, reverseGraph, itemSet, tierMap, scoreMap, chainStartColumn);

            // Move to next available column after this chain
            int maxColumnUsed = chain.stream()
                .filter(columnAssignments::containsKey)
                .mapToInt(columnAssignments::get)
                .max()
                .orElse(chainStartColumn - 1);
            nextColumn = maxColumnUsed + 1;
        }

        LOGGER.info("Assigned {} items to {} progression chains",
            columnAssignments.size(),
            chains.size());

        return columnAssignments;
    }

    /**
     * Build a chain of related items by following dependency connections.
     */
    private static void buildChain(ResourceLocation item, Set<ResourceLocation> chain,
                           Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
                           Map<ResourceLocation, Set<ResourceLocation>> reverseGraph,
                           Set<ResourceLocation> itemSet,
                           Set<ResourceLocation> itemsWithDependencies,
                           Set<ResourceLocation> itemsUsedAsDependencies) {
        if (chain.contains(item)) return;

        // Add this item if it's part of dependency system
        if (itemsWithDependencies.contains(item) || itemsUsedAsDependencies.contains(item)) {
            chain.add(item);

            // Add dependencies
            Set<ResourceLocation> deps = recipeGraph.getOrDefault(item, Collections.emptySet());
            for (ResourceLocation dep : deps) {
                if (itemSet.contains(dep) && itemsUsedAsDependencies.contains(dep)) {
                    buildChain(dep, chain, recipeGraph, reverseGraph, itemSet, itemsWithDependencies, itemsUsedAsDependencies);
                }
            }

            // Add dependents
            Set<ResourceLocation> dependents = reverseGraph.getOrDefault(item, Collections.emptySet());
            for (ResourceLocation dependent : dependents) {
                if (itemSet.contains(dependent) && itemsWithDependencies.contains(dependent)) {
                    buildChain(dependent, chain, recipeGraph, reverseGraph, itemSet, itemsWithDependencies, itemsUsedAsDependencies);
                }
            }
        }
    }

    /**
     * Assign columns to items within a single chain.
     * Columns are assigned in tier order (lower tier = lower column number).
     * Within the same tier, items are sorted by score (lower score = earlier column, weaker items first).
     */
    private static void assignChainColumns(Set<ResourceLocation> chain,
                                    Map<ResourceLocation, Integer> columnAssignments,
                                    Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
                                    Map<ResourceLocation, Set<ResourceLocation>> reverseGraph,
                                    Set<ResourceLocation> itemSet,
                                    Map<ResourceLocation, Integer> tierMap,
                                    Map<ResourceLocation, Double> scoreMap,
                                    int startColumn) {

        Set<ResourceLocation> assigned = new HashSet<>();

        // Sort chain items by tier (ascending), then by score (ascending - weaker first), then by ID for deterministic ordering
        List<ResourceLocation> chainItems = new ArrayList<>(chain);
        chainItems.sort(Comparator.comparing((ResourceLocation item) -> tierMap.getOrDefault(item, Integer.MAX_VALUE))
            .thenComparing((ResourceLocation item) -> scoreMap.getOrDefault(item, 0.0)) // Weaker items first
            .thenComparing(ResourceLocation::toString));

        // First pass: determine which items share columns
        Map<ResourceLocation, ResourceLocation> sharesColumnWith = new HashMap<>();
        for (ResourceLocation item : chainItems) {
            Set<ResourceLocation> usedBy = reverseGraph.getOrDefault(item, Collections.emptySet());

            // Check if this item is used by exactly one item in the chain
            List<ResourceLocation> dependentsInChain = usedBy.stream()
                .filter(chain::contains)
                .collect(Collectors.toList());

            if (dependentsInChain.size() == 1) {
                ResourceLocation dependent = dependentsInChain.get(0);
                // Only share if dependent hasn't already claimed another item
                if (!sharesColumnWith.containsKey(dependent)) {
                    sharesColumnWith.put(dependent, item);
                }
            }
        }

        // Second pass: assign columns in tier order
        int nextColumn = startColumn;
        for (ResourceLocation item : chainItems) {
            if (assigned.contains(item)) continue;

            // Check if this item shares a column with one of its dependencies
            if (sharesColumnWith.containsKey(item)) {
                ResourceLocation sharedDep = sharesColumnWith.get(item);
                if (assigned.contains(sharedDep)) {
                    // Dependency already has a column, share it
                    columnAssignments.put(item, columnAssignments.get(sharedDep));
                    assigned.add(item);
                } else {
                    // Assign new column to both
                    int column = nextColumn++;
                    columnAssignments.put(sharedDep, column);
                    columnAssignments.put(item, column);
                    assigned.add(sharedDep);
                    assigned.add(item);
                }
            } else {
                // Assign own column
                columnAssignments.put(item, nextColumn++);
                assigned.add(item);
            }
        }
    }
}
