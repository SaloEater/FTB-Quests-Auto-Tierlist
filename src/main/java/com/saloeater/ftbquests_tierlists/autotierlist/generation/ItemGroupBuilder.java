package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.autotierlist.analysis.TierCalculator;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 1: Groups items based on progression mode or Armageddon tags.
 *
 * @param <T> The item data type (weapon or armor)
 */
public class ItemGroupBuilder<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Function<T, ResourceLocation> getItemId;
    private final Function<T, ItemStack> getItemStack;
    private final Function<T, Double> getItemScore;

    public ItemGroupBuilder(Function<T, ResourceLocation> getItemId,
                           Function<T, ItemStack> getItemStack,
                           Function<T, Double> getItemScore) {
        this.getItemId = getItemId;
        this.getItemStack = getItemStack;
        this.getItemScore = getItemScore;
    }

    /**
     * Build groups based on current configuration.
     *
     * @param items All tiered items
     * @param recipeGraph Recipe graph (empty if progression disabled)
     * @param tierMap Tier assignments for each item
     * @param enableProgressionAlignment Whether to enable progression-based grouping
     * @return List of item groups
     */
    public List<ItemGroup<T>> buildGroups(
            List<TierCalculator.TieredItem<T>> items,
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
            Map<ResourceLocation, Integer> tierMap,
            boolean enableProgressionAlignment) {

        if (enableProgressionAlignment && !recipeGraph.isEmpty()) {
            return buildProgressionGroups(items, recipeGraph, tierMap);
        } else {
            return buildTagGroups(items);
        }
    }

    /**
     * Build groups for progression mode.
     * Creates PROGRESSION_CHAIN groups for each chain and one ISOLATED group.
     */
    private List<ItemGroup<T>> buildProgressionGroups(
            List<TierCalculator.TieredItem<T>> items,
            Map<ResourceLocation, Set<ResourceLocation>> recipeGraph,
            Map<ResourceLocation, Integer> tierMap) {

        List<ItemGroup<T>> groups = new ArrayList<>();
        Set<ResourceLocation> itemSet = items.stream()
            .map(item -> getItemId.apply(item.data()))
            .collect(Collectors.toSet());

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

        // Find items with dependencies or used as dependencies
        Set<ResourceLocation> itemsWithDependencies = new HashSet<>();
        for (TierCalculator.TieredItem<T> item : items) {
            ResourceLocation itemId = getItemId.apply(item.data());
            if (recipeGraph.containsKey(itemId)) {
                Set<ResourceLocation> ingredients = recipeGraph.get(itemId);
                if (ingredients.stream().anyMatch(itemSet::contains)) {
                    itemsWithDependencies.add(itemId);
                }
            }
        }

        Set<ResourceLocation> itemsUsedAsDependencies = new HashSet<>(reverseGraph.keySet());

        // Build chains
        Map<ResourceLocation, Set<ResourceLocation>> chains = new HashMap<>();
        Set<ResourceLocation> processedItems = new HashSet<>();

        // Sort items with dependencies by tier for consistent ordering
        List<ResourceLocation> withDeps = new ArrayList<>(itemsWithDependencies);
        withDeps.sort(Comparator.comparing(itemId -> tierMap.getOrDefault(itemId, Integer.MAX_VALUE)));

        for (ResourceLocation itemId : withDeps) {
            if (processedItems.contains(itemId)) continue;

            Set<ResourceLocation> chain = new HashSet<>();
            buildChain(itemId, chain, recipeGraph, reverseGraph, itemSet, itemsWithDependencies, itemsUsedAsDependencies);

            if (!chain.isEmpty()) {
                ResourceLocation representative = chain.stream()
                    .min(Comparator.comparing(i -> tierMap.getOrDefault(i, Integer.MAX_VALUE)))
                    .orElse(itemId);
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

        // Create ItemGroup for each chain
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> chainEntry : sortedChains) {
            Set<ResourceLocation> chainItemIds = chainEntry.getValue();

            // Filter items to only those in this chain
            List<T> chainItems = items.stream()
                .filter(item -> chainItemIds.contains(getItemId.apply(item.data())))
                .map(TierCalculator.TieredItem::data)
                .collect(Collectors.toList());

            if (!chainItems.isEmpty()) {
                groups.add(ItemGroup.progressionChain(chainItems, chainItemIds));
            }
        }

        // Create ISOLATED group for items not in any chain
        List<T> isolatedItems = items.stream()
            .filter(item -> !processedItems.contains(getItemId.apply(item.data())))
            .map(TierCalculator.TieredItem::data)
            .collect(Collectors.toList());

        if (!isolatedItems.isEmpty()) {
            groups.add(ItemGroup.isolated(isolatedItems));
        }

        LOGGER.info("Built {} progression groups: {} chains + {} isolated items",
            groups.size(), groups.size() - (isolatedItems.isEmpty() ? 0 : 1), isolatedItems.size());

        return groups;
    }

    /**
     * Build groups for non-progression mode.
     * Creates TAG_GROUP groups for each Armageddon tag and one ISOLATED group.
     */
    private List<ItemGroup<T>> buildTagGroups(List<TierCalculator.TieredItem<T>> items) {
        List<ItemGroup<T>> groups = new ArrayList<>();
        List<AutoTierlistConfig.TagEntry> tagEntries = AutoTierlistConfig.getArmageddonTagEntries();

        Set<T> processedItems = new HashSet<>();

        // Create a group for each Armageddon tag
        for (AutoTierlistConfig.TagEntry tagEntry : tagEntries) {
            List<T> tagItems = new ArrayList<>();

            for (TierCalculator.TieredItem<T> item : items) {
                T itemData = item.data();
                ItemStack stack = getItemStack.apply(itemData);

                // Check if this item has any of this tag entry's tags
                boolean hasTag = tagEntry.getTagKeys().stream()
                    .anyMatch(stack::is);

                if (hasTag && !processedItems.contains(itemData)) {
                    tagItems.add(itemData);
                    processedItems.add(itemData);
                }
            }

            if (!tagItems.isEmpty()) {
                groups.add(ItemGroup.tagGroup(tagItems, tagEntry));
            }
        }

        // Create ISOLATED group for items without any Armageddon tags
        List<T> isolatedItems = items.stream()
            .map(TierCalculator.TieredItem::data)
            .filter(itemData -> !processedItems.contains(itemData))
            .collect(Collectors.toList());

        if (!isolatedItems.isEmpty()) {
            groups.add(ItemGroup.isolated(isolatedItems));
        }

        LOGGER.info("Built {} tag-based groups: {} tag groups + {} isolated items",
            groups.size(), groups.size() - (isolatedItems.isEmpty() ? 0 : 1), isolatedItems.size());

        return groups;
    }

    /**
     * Build a chain of related items by following dependency connections.
     */
    private void buildChain(ResourceLocation item, Set<ResourceLocation> chain,
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
}
