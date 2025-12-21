package com.saloeater.ftbquests_tierlists.autotierlist.config;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TierOverrideManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ResourceLocation, Integer> weaponOverrides = new HashMap<>();
    private final Map<ResourceLocation, Integer> armorOverrides = new HashMap<>();

    public void loadWeaponOverrides(List<? extends String> overrideStrings) {
        weaponOverrides.clear();
        parseOverrides(overrideStrings, weaponOverrides, "weapon");
    }

    public void loadArmorOverrides(List<? extends String> overrideStrings) {
        armorOverrides.clear();
        parseOverrides(overrideStrings, armorOverrides, "armor");
    }

    private void parseOverrides(List<? extends String> overrideStrings,
                               Map<ResourceLocation, Integer> targetMap,
                               String type) {
        for (String override : overrideStrings) {
            try {
                // Format: "modid:itemname=tier"
                String[] parts = override.split("=");
                if (parts.length != 2) {
                    LOGGER.warn("Invalid {} tier override format (expected 'modid:item=tier'): {}", type, override);
                    continue;
                }

                ResourceLocation itemId = new ResourceLocation(parts[0].trim());
                int tier = Integer.parseInt(parts[1].trim());

                // Validate that the item exists
                if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
                    LOGGER.warn("Item '{}' not found in registry, skipping {} tier override", itemId, type);
                    continue;
                }

                // Validate tier is non-negative
                if (tier < 0) {
                    LOGGER.warn("Tier must be non-negative for {}: {} (got {})", type, itemId, tier);
                    continue;
                }

                targetMap.put(itemId, tier);
                LOGGER.debug("Loaded {} tier override: {} = tier {}", type, itemId, tier);

            } catch (Exception e) {
                LOGGER.warn("Failed to parse {} tier override '{}': {}", type, override, e.getMessage());
            }
        }

        LOGGER.info("Loaded {} {} tier overrides", targetMap.size(), type);
    }

    public Optional<Integer> getWeaponOverride(ResourceLocation itemId) {
        return Optional.ofNullable(weaponOverrides.get(itemId));
    }

    public Optional<Integer> getArmorOverride(ResourceLocation itemId) {
        return Optional.ofNullable(armorOverrides.get(itemId));
    }

    public boolean hasWeaponOverride(ResourceLocation itemId) {
        return weaponOverrides.containsKey(itemId);
    }

    public boolean hasArmorOverride(ResourceLocation itemId) {
        return armorOverrides.containsKey(itemId);
    }

    public int getWeaponOverrideCount() {
        return weaponOverrides.size();
    }

    public int getArmorOverrideCount() {
        return armorOverrides.size();
    }
}
