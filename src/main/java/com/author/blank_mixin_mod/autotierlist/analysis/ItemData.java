package com.author.blank_mixin_mod.autotierlist.analysis;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Data records for storing item information for tierlist generation.
 */
public class ItemData {

    /**
     * Weapon data with attack damage.
     */
    public record WeaponData(ResourceLocation id, ItemStack stack, double damage) implements Comparable<WeaponData> {
        @Override
        public int compareTo(WeaponData other) {
            // Sort by damage descending
            return Double.compare(other.damage, this.damage);
        }
    }

    /**
     * Armor data with armor value and toughness.
     */
    public record ArmorData(
        ResourceLocation id,
        ItemStack stack,
        double armor,
        double toughness
    ) implements Comparable<ArmorData> {

        /**
         * Calculate the armor score using the formula: armor * (toughness + 8) / 5
         */
        public double getScore() {
            return armor * (toughness + 8.0) / 5.0;
        }

        @Override
        public int compareTo(ArmorData other) {
            // Sort by score descending
            return Double.compare(other.getScore(), this.getScore());
        }
    }
}
