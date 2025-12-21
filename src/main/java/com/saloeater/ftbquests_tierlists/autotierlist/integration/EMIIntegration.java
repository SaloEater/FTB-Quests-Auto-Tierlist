package com.saloeater.ftbquests_tierlists.autotierlist.integration;

import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;

/**
 * Integration with EMI (Everything May be Itemized) for comprehensive recipe lookup.
 * EMI provides access to all recipe types including vanilla and modded recipes.
 */
public class EMIIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static EmiRecipeManager recipeManager = null;

    /**
     * Initialize EMI integration.
     * Call this when EMI is available.
     */
    public static void initialize() {
        try {
            recipeManager = EmiApi.getRecipeManager();
            LOGGER.info("EMI integration initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize EMI integration", e);
            recipeManager = null;
        }
    }

    /**
     * Check if EMI is available and initialized.
     */
    public static boolean isAvailable() {
        if (recipeManager == null) {
            try {
                recipeManager = EmiApi.getRecipeManager();
            } catch (Exception e) {
                return false;
            }
        }
        return recipeManager != null;
    }

    /**
     * Get recipes that produce the specified items.
     * Builds a graph of output item -> ingredient items.
     *
     * @param relevantItems List of item IDs to check
     * @return Map of output item to set of ingredient items
     */
    public static Map<ResourceLocation, Set<ResourceLocation>> getRecipesUsingItemAsIngredient(
            List<ResourceLocation> relevantItems) {

        if (!isAvailable()) {
            LOGGER.warn("EMI not available, cannot query recipes");
            return new HashMap<>();
        }

        initialize();

        Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
        Set<ResourceLocation> relevantSet = new HashSet<>(relevantItems);

        // Log skipped categories
        List<? extends String> skippedCategories = AutoTierlistConfig.SKIPPED_EMI_CATEGORIES.get();
        if (!skippedCategories.isEmpty()) {
            LOGGER.info("Skipping EMI recipe categories: {}", String.join(", ", skippedCategories));
        }

        try {
            // For each relevant item, find recipes where it's the output
            for (ResourceLocation itemId : relevantItems) {
                ItemStack stack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId));
                if (stack.isEmpty()) continue;

                // Create EMI stack for this item
                EmiStack emiStack = EmiStack.of(stack);

                // Get all recipes that produce this item
                List<EmiRecipe> recipes = recipeManager.getRecipesByOutput(emiStack);

                // Extract ingredients from these recipes, skipping self-referential recipes
                Set<ResourceLocation> ingredients = new HashSet<>();
                for (EmiRecipe recipe : recipes) {
                    if (!isRealRecipe(recipe)) continue;
                    extractIngredients(recipe, itemId, relevantSet, ingredients);
                }

                if (!ingredients.isEmpty()) {
                    recipeGraph.put(itemId, ingredients);
                }
            }

            LOGGER.info("EMI recipe lookup found {} recipes with relevant ingredients", recipeGraph.size());
        } catch (Exception e) {
            LOGGER.error("Error during EMI recipe lookup", e);
        }

        return recipeGraph;
    }

    /**
     * Check if a recipe should be included in crafting chain detection.
     * Returns false if the recipe's category is in the skip list.
     */
    private static boolean isRealRecipe(EmiRecipe recipe) {
        var category = recipe.getCategory();
        String categoryId = category.getId().toString();

        // Check if this category is in the skip list
        for (String skippedCategory : AutoTierlistConfig.SKIPPED_EMI_CATEGORIES.get()) {
            if (categoryId.equals(skippedCategory)) {
                return false;
            }
        }

        String recipeId = "";
        if (recipe.getBackingRecipe() != null) {
            recipeId = recipe.getBackingRecipe().getId().toString();
        }
        var isTrim = recipeId.contains("trim");
        if (isTrim) {
            return false;
        }

        return true;
    }

    /**
     * Extract ingredients from a recipe that are in our relevant items set.
     * Skips ingredients that match the output item (self-referential recipes).
     *
     * @param recipe The recipe to extract from
     * @param outputId The output item of this recipe
     * @param relevantItems Set of items we care about
     * @param ingredients Accumulator for found ingredients
     */
    private static void extractIngredients(EmiRecipe recipe, ResourceLocation outputId,
                                          Set<ResourceLocation> relevantItems,
                                          Set<ResourceLocation> ingredients) {
        try {
            // Get all input ingredients
            for (EmiIngredient ingredient : recipe.getInputs()) {
                if (ingredient.isEmpty()) continue;

                // Each ingredient can represent multiple possible items (tags, etc.)
                for (EmiStack stack : ingredient.getEmiStacks()) {
                    if (stack.isEmpty()) continue;

                    // Get the item ID (Identifier in fabric, ResourceLocation in forge)
                    ResourceLocation ingredientId = new ResourceLocation(
                        stack.getId().getNamespace(),
                        stack.getId().getPath()
                    );

                    // Skip if ingredient is the same as output (self-referential recipe)
                    if (ingredientId.equals(outputId)) {
                        continue;
                    }

                    if (relevantItems.contains(ingredientId)) {
                        ingredients.add(ingredientId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error extracting ingredients from recipe: {}", e.getMessage());
        }
    }
}
