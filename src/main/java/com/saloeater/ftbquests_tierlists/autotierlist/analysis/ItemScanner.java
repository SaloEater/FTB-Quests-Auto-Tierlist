package com.saloeater.ftbquests_tierlists.autotierlist.analysis;

import com.saloeater.ftbquests_tierlists.Tierlists;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Scans all registered items and extracts weapon and armor data.
 */
public class ItemScanner {
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

                // Get attack damage and attack speed attributes for mainhand
                Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
                double damage = getAttributeValue(modifiers, Attributes.ATTACK_DAMAGE);
                double attackSpeed = getAttributeValue(modifiers, Attributes.ATTACK_SPEED);

                // Check if item should be included based on filter
                if (filter.isWeapon(itemId, stack, damage > 0)) {
                    attackSpeed = Attributes.ATTACK_SPEED.getDefaultValue() + attackSpeed;
                    damage = Attributes.ATTACK_DAMAGE.getDefaultValue() + damage;
                    weapons.add(new ItemData.WeaponData(itemId, stack, damage, attackSpeed));
                    Tierlists.LOGGER.debug("Found weapon: {} (damage: {}, attack speed: {}, DPS: {})",
                               itemId, damage, attackSpeed, damage * attackSpeed);
                }
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Error scanning item {}: {}", item, e.getMessage());
            }
        }

        Tierlists.LOGGER.info("Scanned {} weapons", weapons.size());
        return weapons;
    }

    /**
     * Scan all items and find armor based on filter criteria.
     * Only scans chestplates to avoid duplicate entries.
     */
    public List<ItemData.ArmorData> scanArmor() {
        List<ItemData.ArmorData> armors = new ArrayList<>();
        List<Pattern> spellPowerPatterns = compilePatterns(AutoTierlistConfig.SPELL_POWER_ATTRIBUTE_PATTERNS.get());
        List<Pattern> flatSpellPowerPatterns = compilePatterns(AutoTierlistConfig.FLAT_SPELL_POWER_ATTRIBUTE_PATTERNS.get());
        List<Pattern> manaPatterns = compilePatterns(AutoTierlistConfig.MANA_ATTRIBUTE_PATTERNS.get());
        double flatSpellPowerScale = AutoTierlistConfig.FLAT_SPELL_POWER_SCALE.get();

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
                    double mana = sumMatching(modifiers, manaPatterns);
                    // Flat spell power points are scaled to the same fraction as percentage power
                    double spellPower = getSpellPower(modifiers, spellPowerPatterns)
                        + sumMatching(modifiers, flatSpellPowerPatterns) * flatSpellPowerScale;
                    armors.add(new ItemData.ArmorData(itemId, stack, armor, toughness, mana, spellPower));
                    Tierlists.LOGGER.debug("Found armor: {} (armor: {}, toughness: {}, mana: {}, spell power: {})",
                               itemId, armor, toughness, mana, spellPower);
                }
            } catch (Exception e) {
                Tierlists.LOGGER.warn("Error scanning item {}: {}", item, e.getMessage());
            }
        }

        Tierlists.LOGGER.info("Scanned {} armor pieces", armors.size());
        return armors;
    }

    /**
     * Sum the modifiers of every attribute whose registry ID matches one of the patterns.
     * Attributes are matched by registry ID, so no magic mod has to be present.
     */
    private double sumMatching(Multimap<Attribute, AttributeModifier> modifiers, List<Pattern> patterns) {
        double total = 0;
        for (Attribute attribute : modifiers.keySet()) {
            ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (attributeId != null && matchesAny(attributeId, patterns)) {
                total += getAttributeValue(modifiers, attribute);
            }
        }
        return total;
    }

    /**
     * Get the spell power granted by a set of modifiers: plain (school-independent)
     * spell power plus the best single school, i.e. the biggest total any one school gets.
     * Attributes are matched by registry ID, so no magic mod has to be present.
     */
    private double getSpellPower(Multimap<Attribute, AttributeModifier> modifiers, List<Pattern> spellPowerPatterns) {
        double genericPower = 0;
        double bestSchoolPower = 0;
        for (Attribute attribute : modifiers.keySet()) {
            ResourceLocation attributeId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (attributeId == null || !matchesAny(attributeId, spellPowerPatterns)) {
                continue;
            }

            double value = getAttributeValue(modifiers, attribute);
            if (attributeId.getPath().equals("spell_power")) {
                genericPower += value;
            } else {
                bestSchoolPower = Math.max(bestSchoolPower, value);
            }
        }
        return genericPower + bestSchoolPower;
    }

    private static boolean matchesAny(ResourceLocation id, List<Pattern> patterns) {
        String idString = id.toString();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(idString).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compile attribute ID patterns from config. Invalid patterns are logged and dropped.
     */
    private static List<Pattern> compilePatterns(List<? extends String> patternStrings) {
        List<Pattern> compiled = new ArrayList<>();
        for (String patternString : patternStrings) {
            try {
                compiled.add(Pattern.compile(patternString));
            } catch (PatternSyntaxException e) {
                Tierlists.LOGGER.warn("Invalid attribute pattern '{}': {}", patternString, e.getMessage());
            }
        }
        return compiled;
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
