package com.author.blank_mixin_mod.autotierlist.progression;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;

/**
 * Detects crafting relationships between items and assigns them to columns
 * for progression-aligned quest layouts.
 */
public class CraftingChainDetector {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final RecipeManager recipeManager;

    public CraftingChainDetector(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    /**
     * Get the recipe graph for external use (quest dependency creation).
     *
     * @param items List of item IDs to analyze
     * @return Map of output item to set of ingredient items
     */
    public Map<ResourceLocation, Set<ResourceLocation>> getRecipeGraph(List<ResourceLocation> items) {
        return buildRecipeGraph(items);
    }

    /**
     * Assign column numbers to items based on their crafting relationships.
     * Items that share crafting relationships (direct or via shared ingredients)
     * will be placed in the same column.
     *
     * @param items List of item IDs to analyze
     * @return Map of item ID to column number
     */
    public Map<ResourceLocation, Integer> assignColumns(List<ResourceLocation> items) {
        // Build recipe graph
        Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = buildRecipeGraph(items);

        // Use Union-Find to group items with crafting relationships
        UnionFind uf = new UnionFind(items);

        // Connect items with direct crafting relationships
        for (ResourceLocation output : recipeGraph.keySet()) {
            Set<ResourceLocation> ingredients = recipeGraph.get(output);
            for (ResourceLocation ingredient : ingredients) {
                if (items.contains(ingredient)) {
                    uf.union(output, ingredient);
                }
            }
        }

        // Connect items with shared ingredients
        for (ResourceLocation item1 : items) {
            Set<ResourceLocation> ingredients1 = recipeGraph.getOrDefault(item1, Collections.emptySet());
            if (ingredients1.isEmpty()) continue;

            for (ResourceLocation item2 : items) {
                if (item1.equals(item2)) continue;

                Set<ResourceLocation> ingredients2 = recipeGraph.getOrDefault(item2, Collections.emptySet());
                if (!ingredients2.isEmpty() && !Collections.disjoint(ingredients1, ingredients2)) {
                    uf.union(item1, item2);
                }
            }
        }

        // Get groups and assign column numbers
        List<Set<ResourceLocation>> groups = uf.getGroups();
        Map<ResourceLocation, Integer> columnAssignments = new HashMap<>();

        for (int column = 0; column < groups.size(); column++) {
            for (ResourceLocation item : groups.get(column)) {
                columnAssignments.put(item, column);
            }
        }

        LOGGER.info("Detected {} progression columns from {} items", groups.size(), items.size());
        return columnAssignments;
    }

    /**
     * Build a graph of crafting relationships: item -> ingredients.
     *
     * @param relevantItems Only track relationships for these items
     * @return Map of output item to set of ingredient items
     */
    private Map<ResourceLocation, Set<ResourceLocation>> buildRecipeGraph(List<ResourceLocation> relevantItems) {
        Map<ResourceLocation, Set<ResourceLocation>> graph = new HashMap<>();
        Set<ResourceLocation> relevantSet = new HashSet<>(relevantItems);

        try {
            // Iterate through all crafting recipes
            for (Recipe<?> recipe : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
                try {
                    ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(
                        recipe.getResultItem(null).getItem()
                    );

                    // Only track if output is in our relevant items
                    if (outputId == null || !relevantSet.contains(outputId)) {
                        continue;
                    }

                    // Extract all ingredients
                    Set<ResourceLocation> ingredients = new HashSet<>();
                    for (Ingredient ingredient : recipe.getIngredients()) {
                        if (ingredient.isEmpty()) continue;

                        // Get all possible items for this ingredient
                        for (var stack : ingredient.getItems()) {
                            ResourceLocation ingredientId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (ingredientId != null) {
                                ingredients.add(ingredientId);
                            }
                        }
                    }

                    if (!ingredients.isEmpty()) {
                        graph.put(outputId, ingredients);
                    }

                } catch (Exception e) {
                    LOGGER.debug("Error processing recipe {}: {}", recipe.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error building recipe graph", e);
        }

        LOGGER.info("Built recipe graph with {} entries", graph.size());
        return graph;
    }

    /**
     * Union-Find data structure for grouping items with relationships.
     */
    public static class UnionFind {
        private final Map<ResourceLocation, ResourceLocation> parent = new HashMap<>();
        private final Map<ResourceLocation, Integer> rank = new HashMap<>();

        public UnionFind(List<ResourceLocation> items) {
            for (ResourceLocation item : items) {
                parent.put(item, item);
                rank.put(item, 0);
            }
        }

        /**
         * Find the root of an item's set (with path compression).
         */
        public ResourceLocation find(ResourceLocation item) {
            if (!parent.get(item).equals(item)) {
                parent.put(item, find(parent.get(item))); // Path compression
            }
            return parent.get(item);
        }

        /**
         * Union two items' sets (by rank).
         */
        public void union(ResourceLocation item1, ResourceLocation item2) {
            ResourceLocation root1 = find(item1);
            ResourceLocation root2 = find(item2);

            if (root1.equals(root2)) {
                return; // Already in same set
            }

            // Union by rank
            int rank1 = rank.get(root1);
            int rank2 = rank.get(root2);

            if (rank1 < rank2) {
                parent.put(root1, root2);
            } else if (rank1 > rank2) {
                parent.put(root2, root1);
            } else {
                parent.put(root2, root1);
                rank.put(root1, rank1 + 1);
            }
        }

        /**
         * Get all groups (sets of items with relationships).
         */
        public List<Set<ResourceLocation>> getGroups() {
            Map<ResourceLocation, Set<ResourceLocation>> groupMap = new HashMap<>();

            for (ResourceLocation item : parent.keySet()) {
                ResourceLocation root = find(item);
                groupMap.computeIfAbsent(root, k -> new HashSet<>()).add(item);
            }

            return new ArrayList<>(groupMap.values());
        }
    }
}
