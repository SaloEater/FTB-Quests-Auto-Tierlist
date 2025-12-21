package com.saloeater.ftbquests_tierlists.autotierlist.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose SmithingTransformRecipe's private fields.
 * Used by JEI integration to extract ingredients for crafting chain detection.
 */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    @Accessor("template")
    Ingredient getTemplate();

    @Accessor("base")
    Ingredient getBase();

    @Accessor("addition")
    Ingredient getAddition();
}
