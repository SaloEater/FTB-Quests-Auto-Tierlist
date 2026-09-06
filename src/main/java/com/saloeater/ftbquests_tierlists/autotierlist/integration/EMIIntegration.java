package com.saloeater.ftbquests_tierlists.autotierlist.integration;

import com.saloeater.ftbquests_tierlists.Tierlists;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Integration with EMI (Everything May be Itemized) for comprehensive recipe lookup.
 * EMI provides access to all recipe types including vanilla and modded recipes.
 */
public class EMIIntegration {
    private static EmiRecipeManager recipeManager = null;

    /** Compiled form of SKIPPED_RECIPE_PATTERNS, recompiled when the config list changes. */
    private static List<? extends String> patternSource = null;
    private static List<Pattern> skippedRecipePatterns = List.of();

    /** Extra skip patterns for a single generation run, supplied as a command argument. */
    private static List<Pattern> extraSkippedRecipePatterns = List.of();

    /**
     * Initialize EMI integration.
     * Call this when EMI is available.
     */
    public static void initialize() {
        try {
            recipeManager = EmiApi.getRecipeManager();
            Tierlists.LOGGER.info("EMI integration initialized successfully");
        } catch (Exception e) {
            Tierlists.LOGGER.error("Failed to initialize EMI integration", e);
            recipeManager = null;
        }
    }

    /**
     * Set extra recipe skip patterns that apply until {@link #clearExtraSkippedRecipePatterns()}
     * is called. These are added on top of the configured patterns, not a replacement.
     * Invalid patterns are logged and dropped.
     *
     * @return The number of patterns that compiled successfully
     */
    public static int setExtraSkippedRecipePatterns(List<String> patternStrings) {
        List<Pattern> compiled = new ArrayList<>();
        for (String patternString : patternStrings) {
            try {
                compiled.add(Pattern.compile(patternString));
            } catch (PatternSyntaxException e) {
                Tierlists.LOGGER.warn("Invalid skipped recipe pattern '{}': {}", patternString, e.getMessage());
            }
        }
        extraSkippedRecipePatterns = compiled;
        return compiled.size();
    }

    /**
     * Drop the extra recipe skip patterns, restoring config-only behaviour.
     */
    public static void clearExtraSkippedRecipePatterns() {
        extraSkippedRecipePatterns = List.of();
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
            Tierlists.LOGGER.warn("EMI not available, cannot query recipes");
            return new HashMap<>();
        }

        initialize();

        Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
        Set<ResourceLocation> relevantSet = new HashSet<>(relevantItems);

        // Log skipped categories
        List<? extends String> skippedCategories = AutoTierlistConfig.SKIPPED_EMI_CATEGORIES.get();
        if (!skippedCategories.isEmpty()) {
            Tierlists.LOGGER.info("Skipping EMI recipe categories: {}", String.join(", ", skippedCategories));
        }
        List<? extends String> skippedPatterns = AutoTierlistConfig.SKIPPED_RECIPE_PATTERNS.get();
        if (!skippedPatterns.isEmpty()) {
            Tierlists.LOGGER.info("Skipping EMI recipe IDs matching: {}", String.join(", ", skippedPatterns));
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

            Tierlists.LOGGER.info("EMI recipe lookup found {} recipes with relevant ingredients", recipeGraph.size());
        } catch (Exception e) {
            Tierlists.LOGGER.error("Error during EMI recipe lookup", e);
        }

        return recipeGraph;
    }

    /**
     * One recipe linking the queried item to other items in the tierlist pool.
     *
     * @param recipeId   The EMI recipe ID, or null if the recipe has none
     * @param categoryId The EMI category the recipe is displayed under
     * @param skipped    Whether this recipe is currently filtered out of crafting chains
     * @param items      The pool items this recipe connects the queried item to
     */
    public record RecipeConnection(ResourceLocation recipeId, String categoryId, boolean skipped,
                                   List<ResourceLocation> items) {
    }

    /**
     * Find the recipes that produce the given item, reporting which pool items each one
     * pulls in as an ingredient. Skipped recipes are included and flagged, so the result
     * shows both the connections that exist and the ones the filters removed.
     *
     * @param itemId        The item to inspect
     * @param relevantItems The tierlist item pool
     */
    public static List<RecipeConnection> getConnectionsForOutput(ResourceLocation itemId,
                                                                 Set<ResourceLocation> relevantItems) {
        return getConnections(itemId, relevantItems, true);
    }

    /**
     * Find the recipes that consume the given item, reporting which pool items each one
     * produces. This is the reverse direction of {@link #getConnectionsForOutput}.
     *
     * @param itemId        The item to inspect
     * @param relevantItems The tierlist item pool
     */
    public static List<RecipeConnection> getConnectionsForInput(ResourceLocation itemId,
                                                                Set<ResourceLocation> relevantItems) {
        return getConnections(itemId, relevantItems, false);
    }

    /**
     * Shared implementation of the two connection lookups.
     *
     * @param asOutput True to look up recipes producing the item and report their ingredients,
     *                 false to look up recipes consuming it and report their outputs
     */
    private static List<RecipeConnection> getConnections(ResourceLocation itemId,
                                                         Set<ResourceLocation> relevantItems,
                                                         boolean asOutput) {
        if (!isAvailable()) {
            Tierlists.LOGGER.warn("EMI not available, cannot query recipes");
            return List.of();
        }

        ItemStack stack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId));
        if (stack.isEmpty()) return List.of();

        EmiStack emiStack = EmiStack.of(stack);
        List<EmiRecipe> recipes = asOutput
            ? recipeManager.getRecipesByOutput(emiStack)
            : recipeManager.getRecipesByInput(emiStack);

        List<RecipeConnection> connections = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            Set<ResourceLocation> linked = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
            if (asOutput) {
                extractIngredients(recipe, itemId, relevantItems, linked);
            } else {
                for (EmiStack output : recipe.getOutputs()) {
                    if (output.isEmpty()) continue;
                    ResourceLocation outputId = new ResourceLocation(
                        output.getId().getNamespace(), output.getId().getPath());
                    if (outputId.equals(itemId)) continue;
                    if (relevantItems.contains(outputId)) {
                        linked.add(outputId);
                    }
                }
            }

            connections.add(new RecipeConnection(
                recipe.getId(),
                recipe.getCategory().getId().toString(),
                !isRealRecipe(recipe),
                new ArrayList<>(linked)));
        }

        return connections;
    }

    /**
     * Check if a recipe should be included in crafting chain detection.
     * Returns false if the recipe's category is in the skip list, or if its ID
     * matches one of the configured or per-run skip patterns.
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

        // Check the recipe ID against the skip patterns. Use getId() rather than
        // getBackingRecipe(), which only resolves recipes registered in the vanilla
        // RecipeManager and is null for every synthetic (modded machine) recipe.
        ResourceLocation recipeId = recipe.getId();
        if (recipeId != null) {
            String id = recipeId.toString();
            for (Pattern pattern : getSkippedRecipePatterns()) {
                if (pattern.matcher(id).find()) {
                    return false;
                }
            }
            for (Pattern pattern : extraSkippedRecipePatterns) {
                if (pattern.matcher(id).find()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get the compiled skip patterns, recompiling only when the config list changed.
     * Invalid patterns are logged and dropped.
     */
    private static List<Pattern> getSkippedRecipePatterns() {
        List<? extends String> raw = AutoTierlistConfig.SKIPPED_RECIPE_PATTERNS.get();
        if (!raw.equals(patternSource)) {
            List<Pattern> compiled = new ArrayList<>();
            for (String patternString : raw) {
                try {
                    compiled.add(Pattern.compile(patternString));
                } catch (PatternSyntaxException e) {
                    Tierlists.LOGGER.warn("Invalid skipped recipe pattern '{}': {}", patternString, e.getMessage());
                }
            }
            patternSource = raw;
            skippedRecipePatterns = compiled;
        }
        return skippedRecipePatterns;
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
            Tierlists.LOGGER.debug("Error extracting ingredients from recipe: {}", e.getMessage());
        }
    }
}
