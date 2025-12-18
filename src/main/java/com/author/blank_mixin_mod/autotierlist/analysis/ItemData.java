package com.author.blank_mixin_mod.autotierlist.analysis;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Data records for storing item information for tierlist generation.
 */
public class ItemData {

    /**
     * Weapon data with attack damage and attack speed.
     */
    public record WeaponData(ResourceLocation id, ItemStack stack, double damage, double attackSpeed) implements Comparable<WeaponData> {

        /**
         * Calculate DPS (damage per second) using the formula: damage * attackSpeed
         */
        public double getDPS() {
            return damage * attackSpeed;
        }

        @Override
        public int compareTo(WeaponData other) {
            // Sort by DPS descending
            return Double.compare(other.getDPS(), this.getDPS());
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
         * Calculate the armor score using the formula: armor * (toughness + 8) / 5, from wiki
         */
        public double getScore() {
            return armor + toughness * 0.6;
        }

        @Override
        public int compareTo(ArmorData other) {
            // Sort by score descending
            return Double.compare(other.getScore(), this.getScore());
        }
    }
}
