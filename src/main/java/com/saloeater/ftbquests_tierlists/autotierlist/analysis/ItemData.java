package com.saloeater.ftbquests_tierlists.autotierlist.analysis;

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
     * Armor data with armor value and toughness, plus the magic stats the piece grants.
     *
     * @param mana       Max mana granted by the piece
     * @param spellPower Spell power granted by the piece as a fraction (0.15 = +15%):
     *                   plain spell power (flat points already scaled in) plus the best single school
     */
    public record ArmorData(
        ResourceLocation id,
        ItemStack stack,
        double armor,
        double toughness,
        double mana,
        double spellPower
    ) implements Comparable<ArmorData> {

        /**
         * Whether the piece grants any spell power or mana.
         */
        public boolean isMagic() {
            return mana > 0 || spellPower > 0;
        }

        /**
         * Calculate the magic score:
         * spellPower * spellPowerWeight + mana * manaWeight + armor score * armorWeight
         */
        public double getMagicScore(double spellPowerWeight, double manaWeight, double armorWeight) {
            return Math.max(0, spellPower * spellPowerWeight + mana * manaWeight + getScore() * armorWeight);
        }

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
