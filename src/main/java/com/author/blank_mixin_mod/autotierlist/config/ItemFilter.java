package com.author.blank_mixin_mod.autotierlist.config;

import com.mojang.logging.LogUtils;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<TagKey<Item>> weaponTags = new HashSet<>();
    private final Set<TagKey<Item>> armorTags = new HashSet<>();
    private final Set<ResourceLocation> weaponItems = new HashSet<>();
    private final Set<ResourceLocation> armorItems = new HashSet<>();
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
                LOGGER.debug("Loaded weapon tag: {}", tagString);
            } catch (Exception e) {
                LOGGER.warn("Invalid weapon tag '{}': {}", tagString, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} weapon tags", weaponTags.size());
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
                LOGGER.debug("Loaded armor tag: {}", tagString);
            } catch (Exception e) {
                LOGGER.warn("Invalid armor tag '{}': {}", tagString, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} armor tags", armorTags.size());
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
                    LOGGER.debug("Loaded weapon item: {}", itemString);
                } else {
                    LOGGER.warn("Item '{}' not found in registry", itemString);
                }
            } catch (Exception e) {
                LOGGER.warn("Invalid weapon item '{}': {}", itemString, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} manual weapon items", weaponItems.size());
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
                    LOGGER.debug("Loaded armor item: {}", itemString);
                } else {
                    LOGGER.warn("Item '{}' not found in registry", itemString);
                }
            } catch (Exception e) {
                LOGGER.warn("Invalid armor item '{}': {}", itemString, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} manual armor items", armorItems.size());
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
        return String.format("Weapon filters: %d tags, %d items | Armor filters: %d tags, %d items | Attribute detection: %s",
            weaponTags.size(), weaponItems.size(), armorTags.size(), armorItems.size(),
            useAttributeDetection ? "enabled" : "disabled");
    }
}
