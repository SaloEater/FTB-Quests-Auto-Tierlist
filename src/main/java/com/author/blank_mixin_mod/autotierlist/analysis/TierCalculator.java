package com.author.blank_mixin_mod.autotierlist.analysis;

import com.author.blank_mixin_mod.autotierlist.config.TierOverrideManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Calculates tier assignments for weapons and armor based on their attributes.
 */
public class TierCalculator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final double tierMultiplier;
    private final int rowsPerTier;
    private final TierOverrideManager overrideManager;

    public TierCalculator(double tierMultiplier, int rowsPerTier, TierOverrideManager overrideManager) {
        this.tierMultiplier = tierMultiplier;
        this.rowsPerTier = rowsPerTier;
        this.overrideManager = overrideManager;
    }

    /**
     * Assign weapons to tiers based on their DPS (damage * attack speed).
     * Tier formula: tier = floor(DPS / tierMultiplier)
     */
    public Map<Integer, List<TieredItem<ItemData.WeaponData>>> assignWeaponTiers(List<ItemData.WeaponData> weapons) {
        Map<Integer, List<TieredItem<ItemData.WeaponData>>> tierMap = new HashMap<>();

        for (ItemData.WeaponData weapon : weapons) {
            double dps = weapon.getDPS();

            // Check for manual override first
            int tier = overrideManager.getWeaponOverride(weapon.id())
                .orElseGet(() -> calculateTier(dps));

            // Calculate which row within the tier (0 = bottom, rowsPerTier-1 = top)
            int row = calculateRowInTier(dps, tier);

            tierMap.computeIfAbsent(tier, k -> new ArrayList<>())
                .add(new TieredItem<>(weapon, tier, row));
        }

        // Sort each tier's items by row (and then by DPS within the row)
        for (List<TieredItem<ItemData.WeaponData>> items : tierMap.values()) {
            items.sort(Comparator.comparingInt(TieredItem<ItemData.WeaponData>::row)
                .thenComparing(item -> -item.data().getDPS())); // Descending DPS within row
        }

        LOGGER.info("Assigned {} weapons to {} tiers", weapons.size(), tierMap.size());
        return tierMap;
    }

    /**
     * Assign armor to tiers based on their armor score.
     * Score formula: armor * (toughness + 8) / 5
     * Tier formula: tier = floor(score / tierMultiplier)
     */
    public Map<Integer, List<TieredItem<ItemData.ArmorData>>> assignArmorTiers(List<ItemData.ArmorData> armors) {
        Map<Integer, List<TieredItem<ItemData.ArmorData>>> tierMap = new HashMap<>();

        for (ItemData.ArmorData armor : armors) {
            // Check for manual override first
            int tier = overrideManager.getArmorOverride(armor.id())
                .orElseGet(() -> {
                    double score = armor.getScore();
                    return (int) Math.round(score);
                });

            // Calculate which row within the tier
            int row = calculateRowInTier(armor.getScore(), tier);

            tierMap.computeIfAbsent(tier, k -> new ArrayList<>())
                .add(new TieredItem<>(armor, tier, row));
        }

        // Sort each tier's items by row (and then by score within the row)
        for (List<TieredItem<ItemData.ArmorData>> items : tierMap.values()) {
            items.sort(Comparator.comparingInt(TieredItem<ItemData.ArmorData>::row)
                .thenComparing(item -> -item.data().getScore())); // Descending score within row
        }

        LOGGER.info("Assigned {} armor pieces to {} tiers", armors.size(), tierMap.size());
        return tierMap;
    }

    /**
     * Calculate tier from a value using the formula: floor(value / tierMultiplier)
     */
    private int calculateTier(double value) {
        return (int) Math.floor(value / tierMultiplier);
    }

    /**
     * Calculate which row within a tier an item should be placed in.
     * Items closer to the next tier are placed in higher rows.
     *
     * @param value The item's attribute value
     * @param tier The tier the item belongs to
     * @return Row index (0 = bottom/far from next tier, rowsPerTier-1 = top/close to next tier)
     */
    private int calculateRowInTier(double value, int tier) {
        return 0;
        /*double tierMin = tier * tierMultiplier;
        double tierMax = (tier + 1) * tierMultiplier;
        double range = tierMax - tierMin;

        // Calculate position within tier (0.0 = at minimum, 1.0 = at maximum)
        double position = (value - tierMin) / range;

        // Clamp to [0.0, 1.0] to handle edge cases
        position = Math.max(0.0, Math.min(1.0, position));

        // Assign to row (higher position = higher row = closer to next tier)
        int row = (int) (position * rowsPerTier);

        // Ensure row is within bounds
        return Math.min(row, rowsPerTier - 1);*/
    }

    /**
     * Container class for an item with its tier and row assignment.
     */
    public record TieredItem<T>(T data, int tier, int row) {}
}
