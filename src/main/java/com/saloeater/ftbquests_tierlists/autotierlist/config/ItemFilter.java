package com.saloeater.ftbquests_tierlists.autotierlist.config;

import com.mojang.logging.LogUtils;
import com.saloeater.ftbquests_tierlists.Tierlists;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filters items based on tags and manual item lists from config.
 */
public class ItemFilter {
    private final Set<TagKey<Item>> weaponTags = new HashSet<>();
    private final Set<TagKey<Item>> armorTags = new HashSet<>();
    private final Set<ResourceLocation> weaponItems = new HashSet<>();
    private final Set<ResourceLocation> armorItems = new HashSet<>();
    private final Set<ResourceLocation> skippedItems = new HashSet<>();
    private final boolean useAttributeDetection;

    public ItemFilter(boolean useAttributeDetection) {
        this.useAttributeDetection = useAttributeDetection;
    }

    /**
     * Load weapon tags from config.
     */
    public void loadWeaponTags(List<? extends String> tagStrings) {
        weaponTags.clear();
        for (String tagString : tagStrings) {
            try {
                ResourceLocation tagId = new ResourceLocation(tagString);
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                weaponTags.add(tag);
                Tierlists.LOGGER.debug("Loaded weapon tag: {}", tagString);
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Invalid weapon tag '{}': {}", tagString, e.getMessage());
            }
        }
        Tierlists.LOGGER.info("Loaded {} weapon tags", weaponTags.size());
    }

    /**
     * Load armor tags from config.
     */
    public void loadArmorTags(List<? extends String> tagStrings) {
        armorTags.clear();
        for (String tagString : tagStrings) {
            try {
                ResourceLocation tagId = new ResourceLocation(tagString);
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                armorTags.add(tag);
                Tierlists.LOGGER.debug("Loaded armor tag: {}", tagString);
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Invalid armor tag '{}': {}", tagString, e.getMessage());
            }
        }
        Tierlists.LOGGER.info("Loaded {} armor tags", armorTags.size());
    }

    /**
     * Load manually specified weapon items from config.
     */
    public void loadWeaponItems(List<? extends String> itemStrings) {
        weaponItems.clear();
        for (String itemString : itemStrings) {
            try {
                ResourceLocation itemId = new ResourceLocation(itemString);
                if (ForgeRegistries.ITEMS.containsKey(itemId)) {
                    weaponItems.add(itemId);
                    Tierlists.LOGGER.debug("Loaded weapon item: {}", itemString);
                } else {
                    Tierlists.LOGGER.warn("Item '{}' not found in registry", itemString);
                }
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Invalid weapon item '{}': {}", itemString, e.getMessage());
            }
        }
        Tierlists.LOGGER.info("Loaded {} manual weapon items", weaponItems.size());
    }

    /**
     * Load manually specified armor items from config.
     */
    public void loadArmorItems(List<? extends String> itemStrings) {
        armorItems.clear();
        for (String itemString : itemStrings) {
            try {
                ResourceLocation itemId = new ResourceLocation(itemString);
                if (ForgeRegistries.ITEMS.containsKey(itemId)) {
                    armorItems.add(itemId);
                    Tierlists.LOGGER.debug("Loaded armor item: {}", itemString);
                } else {
                    Tierlists.LOGGER.warn("Item '{}' not found in registry", itemString);
                }
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Invalid armor item '{}': {}", itemString, e.getMessage());
            }
        }
        Tierlists.LOGGER.info("Loaded {} manual armor items", armorItems.size());
    }

    /**
     * Load items to skip from config.
     * These items will be excluded from all tierlists.
     */
    public void loadSkippedItems(List<? extends String> itemStrings) {
        skippedItems.clear();
        for (String itemString : itemStrings) {
            try {
                ResourceLocation itemId = new ResourceLocation(itemString);
                if (ForgeRegistries.ITEMS.containsKey(itemId)) {
                    skippedItems.add(itemId);
                    Tierlists.LOGGER.debug("Loaded skipped item: {}", itemString);
                } else {
                    Tierlists.LOGGER.warn("Item '{}' not found in registry", itemString);
                }
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Invalid skipped item '{}': {}", itemString, e.getMessage());
            }
        }
        Tierlists.LOGGER.info("Loaded {} items to skip", skippedItems.size());
    }

    /**
     * Check if an item should be included in the weapon tierlist.
     *
     * @param itemId The item's resource location
     * @param stack The item stack
     * @param hasAttackDamage Whether the item has attack damage > 0
     * @return True if the item should be included
     */
    public boolean isWeapon(ResourceLocation itemId, ItemStack stack, boolean hasAttackDamage) {
        // Check if item is in skip list
        if (skippedItems.contains(itemId)) {
            return false;
        }

        // Check manual item list
        if (weaponItems.contains(itemId)) {
            return true;
        }

        // Check tags
        if (!weaponTags.isEmpty()) {
            for (TagKey<Item> tag : weaponTags) {
                if (stack.is(tag)) {
                    return true;
                }
            }
        }

        // Use attribute detection if enabled and no tags/items specified, or as fallback
        if (useAttributeDetection && (weaponTags.isEmpty() && weaponItems.isEmpty())) {
            return hasAttackDamage;
        }

        return false;
    }

    /**
     * Check if an item should be included in the armor tierlist.
     *
     * @param itemId The item's resource location
     * @param stack The item stack
     * @param hasArmorValue Whether the item has armor value > 0
     * @return True if the item should be included
     */
    public boolean isArmor(ResourceLocation itemId, ItemStack stack, boolean hasArmorValue) {
        // Check if item is in skip list
        if (skippedItems.contains(itemId)) {
            return false;
        }

        // Check manual item list
        if (armorItems.contains(itemId)) {
            return true;
        }

        // Check tags
        if (!armorTags.isEmpty()) {
            for (TagKey<Item> tag : armorTags) {
                if (stack.is(tag)) {
                    return true;
                }
            }
        }

        // Use attribute detection if enabled and no tags/items specified, or as fallback
        if (useAttributeDetection && (armorTags.isEmpty() && armorItems.isEmpty())) {
            return hasArmorValue;
        }

        return false;
    }

    /**
     * Get statistics about loaded filters.
     */
    public String getStats() {
        return String.format("Weapon filters: %d tags, %d items | Armor filters: %d tags, %d items | Skipped items: %d | Attribute detection: %s",
            weaponTags.size(), weaponItems.size(), armorTags.size(), armorItems.size(), skippedItems.size(),
            useAttributeDetection ? "enabled" : "disabled");
    }
}
