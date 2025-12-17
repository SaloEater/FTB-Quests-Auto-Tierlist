package com.author.blank_mixin_mod.autotierlist.analysis;

import com.author.blank_mixin_mod.autotierlist.config.ItemFilter;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans all registered items and extracts weapon and armor data.
 */
public class ItemScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Only scan chestplates for armor tierlist to avoid duplicates
    private static final EquipmentSlot ARMOR_SLOT = EquipmentSlot.CHEST;

    private final ItemFilter filter;

    public ItemScanner(ItemFilter filter) {
        this.filter = filter;
    }

    /**
     * Scan all items and find weapons based on filter criteria.
     */
    public List<ItemData.WeaponData> scanWeapons() {
        List<ItemData.WeaponData> weapons = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS) {
            try {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);

                // Skip air and invalid items
                if (itemId == null || itemId.equals(new ResourceLocation("minecraft:air"))) {
                    continue;
                }

                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }

                // Get attack damage attribute for mainhand
                Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
                double damage = getAttributeValue(modifiers, Attributes.ATTACK_DAMAGE);

                // Check if item should be included based on filter
                if (filter.isWeapon(itemId, stack, damage > 0)) {
                    // If damage is 0 but item passed filter (tag/manual list), still need damage value
                    // Use 0 as damage - it will be placed in tier 0
                    weapons.add(new ItemData.WeaponData(itemId, stack, Math.max(damage, 0)));
                    LOGGER.debug("Found weapon: {} (damage: {})", itemId, damage);
                }
            } catch (Exception e) {
                LOGGER.warn("Error scanning item {}: {}", item, e.getMessage());
            }
        }

        LOGGER.info("Scanned {} weapons", weapons.size());
        return weapons;
    }

    /**
     * Scan all items and find armor based on filter criteria.
     * Only scans chestplates to avoid duplicate entries.
     */
    public List<ItemData.ArmorData> scanArmor() {
        List<ItemData.ArmorData> armors = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS) {
            try {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);

                // Skip air and invalid items
                if (itemId == null || itemId.equals(new ResourceLocation("minecraft:air"))) {
                    continue;
                }

                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }

                // Get armor attributes for chest slot only
                Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(ARMOR_SLOT);
                double armor = getAttributeValue(modifiers, Attributes.ARMOR);
                double toughness = getAttributeValue(modifiers, Attributes.ARMOR_TOUGHNESS);

                // Check if item should be included based on filter
                if (filter.isArmor(itemId, stack, armor > 0)) {
                    // If armor is 0 but item passed filter (tag/manual list), still need values
                    armors.add(new ItemData.ArmorData(itemId, stack, Math.max(armor, 0), toughness));
                    LOGGER.debug("Found armor: {} (armor: {}, toughness: {})",
                               itemId, armor, toughness);
                }
            } catch (Exception e) {
                LOGGER.warn("Error scanning item {}: {}", item, e.getMessage());
            }
        }

        LOGGER.info("Scanned {} armor pieces", armors.size());
        return armors;
    }

    /**
     * Extract the total value of an attribute from a multimap of modifiers.
     */
    private double getAttributeValue(Multimap<Attribute, AttributeModifier> modifiers, Attribute attribute) {
        return modifiers.get(attribute).stream()
            .mapToDouble(AttributeModifier::getAmount)
            .sum();
    }
}
