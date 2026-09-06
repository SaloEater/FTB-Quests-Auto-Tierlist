package com.saloeater.ftbquests_tierlists.autotierlist.analysis;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraftforge.common.ToolActions;

/**
 * Heuristics for classifying items as weapons vs tools.
 *
 * Minecraft has no canonical weapon/tool API, so classification is layered:
 * item class hierarchy, Forge ToolActions, and vanilla tags.
 */
public class WeaponClassifier {

    /**
     * The community convention tag for melee weapons, targeted by the KubeJS export.
     */
    public static final TagKey<Item> MELEE_WEAPONS_TAG =
        TagKey.create(Registries.ITEM, new ResourceLocation("c", "tools/melee_weapons"));

    /**
     * Check whether an item is a pickaxe, shovel, or hoe by class hierarchy,
     * Forge ToolActions, or vanilla tags. Axes are NOT considered tool-like here,
     * since they are commonly used as weapons.
     */
    public static boolean isToolLike(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) {
            return true;
        }
        if (stack.canPerformAction(ToolActions.PICKAXE_DIG)
            || stack.canPerformAction(ToolActions.SHOVEL_DIG)
            || stack.canPerformAction(ToolActions.HOE_DIG)) {
            return true;
        }
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }
}
