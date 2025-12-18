package com.author.blank_mixin_mod.autotierlist.integration;

import com.author.blank_mixin_mod.autotierlist.mixin.SmithingTransformRecipeAccessor;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Integration with Just Enough Items (JEI) for comprehensive recipe lookup.
 * Provides access to all recipe types that JEI knows about, not just vanilla crafting.
 */
@JeiPlugin
public class JEIIntegration implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation PLUGIN_UID = new ResourceLocation("blank_mixin_mod", "autotierlist_jei");

    private static IJeiRuntime jeiRuntime = null;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        LOGGER.info("JEI runtime available for Auto-Tierlist");
        JEIIntegration.jeiRuntime = jeiRuntime;
    }

    /**
     * Check if JEI is available and loaded.
     */
    public static boolean isAvailable() {
        return jeiRuntime != null;
    }

    /**
     * Get all recipes where the given item is used as an ingredient (INPUT role).
     * This searches across ALL recipe types that JEI knows about.
     *
     * @param itemStack The item to search for as an ingredient
     * @return Map of output item IDs to their ingredient item IDs (items used to craft them)
     */
    public static Map<ResourceLocation, Set<ResourceLocation>> getRecipesUsingItemAsIngredient(
            List<ResourceLocation> relevantItems) {

        if (!isAvailable()) {
            LOGGER.warn("JEI not available, cannot query recipes");
            return Collections.emptyMap();
        }

        Map<ResourceLocation, Set<ResourceLocation>> recipeGraph = new HashMap<>();
        Set<ResourceLocation> relevantSet = new HashSet<>(relevantItems);

        try {
            var recipeManager = jeiRuntime.getRecipeManager();
            var jeiHelpers = jeiRuntime.getJeiHelpers();
            var focusFactory = jeiHelpers.getFocusFactory();

            // Get all available recipe types from JEI
            var allRecipeTypes = jeiHelpers.getAllRecipeTypes().collect(Collectors.toList());
            LOGGER.debug("Found {} recipe types in JEI", allRecipeTypes.size());

            // For each relevant item, find recipes where it's the output
            for (ResourceLocation itemId : relevantItems) {
                ItemStack stack = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId));
                if (stack.isEmpty()) continue;

                // Create focus for this item as OUTPUT
                IFocus<ItemStack> outputFocus = focusFactory.createFocus(
                    RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK,
                    stack
                );

                // Get all recipe categories that produce this item
                // First limit to all recipe types, then apply focus
                var categoryLookup = recipeManager.createRecipeCategoryLookup();
                var categories = categoryLookup
                    .limitTypes(allRecipeTypes)
                    .limitFocus(Collections.singleton(outputFocus))
                    .get()
                    .collect(Collectors.toList());

                // For each category, get the actual recipes
                Set<ResourceLocation> ingredients = new HashSet<>();
                for (var category : categories) {
                    var recipeLookup = recipeManager.createRecipeLookup(category.getRecipeType());
                    var recipes = recipeLookup.limitFocus(Collections.singleton(outputFocus)).get();

                    // Extract ingredients from each recipe
                    recipes.forEach(recipe -> {
                        extractIngredients(recipe, relevantSet, ingredients);
                    });
                }

                if (!ingredients.isEmpty()) {
                    recipeGraph.put(itemId, ingredients);
                }
            }

            LOGGER.info("JEI recipe lookup found {} recipes with relevant ingredients", recipeGraph.size());
        } catch (Exception e) {
            LOGGER.error("Error querying JEI recipes", e);
        }

        return recipeGraph;
    }

    /**
     * Extract ingredients from a recipe object.
     * Handles both vanilla Recipe<?> instances and JEI-specific recipe objects.
     */
    private static void extractIngredients(Object recipe, Set<ResourceLocation> relevantItems,
                                          Set<ResourceLocation> ingredients) {
        try {
            // Handle vanilla Recipe<?> instances
            if (recipe instanceof SmithingTransformRecipe smithingRecipe) {
                var accessor = (SmithingTransformRecipeAccessor) smithingRecipe;
                extractIngredient(accessor.getTemplate(), relevantItems, ingredients);
                extractIngredient(accessor.getBase(), relevantItems, ingredients);
                extractIngredient(accessor.getAddition(), relevantItems, ingredients);
            } else if (recipe instanceof Recipe<?> vanillaRecipe){
                // Standard recipe handling
                for (var ingredient : vanillaRecipe.getIngredients()) {
                    extractIngredient(ingredient, relevantItems, ingredients);
                }
            }
            // For non-vanilla recipes, we'd need specific handlers per mod
            // For now, log that we encountered a non-vanilla recipe
            else {
                LOGGER.debug("Encountered non-vanilla recipe type: {}", recipe.getClass().getName());
            }
        } catch (Exception e) {
            LOGGER.debug("Error extracting ingredients from recipe: {}", e.getMessage());
        }
    }

    /**
     * Extract items from a single ingredient.
     */
    private static void extractIngredient(net.minecraft.world.item.crafting.Ingredient ingredient,
                                         Set<ResourceLocation> relevantItems,
                                         Set<ResourceLocation> ingredients) {
        if (ingredient.isEmpty()) return;

        for (var stack : ingredient.getItems()) {
            ResourceLocation ingredientId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (ingredientId != null && relevantItems.contains(ingredientId)) {
                ingredients.add(ingredientId);
            }
        }
    }

    /**
     * Get the JEI runtime instance (if available).
     * This can be used for custom recipe queries.
     */
    public static Optional<IJeiRuntime> getRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }
}
