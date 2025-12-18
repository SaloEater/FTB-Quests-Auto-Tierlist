package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.analysis.ItemData;
import com.author.blank_mixin_mod.autotierlist.analysis.ItemScanner;
import com.author.blank_mixin_mod.autotierlist.analysis.TierCalculator;
import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import com.author.blank_mixin_mod.autotierlist.config.ItemFilter;
import com.author.blank_mixin_mod.autotierlist.config.TierOverrideManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Generates the weapon tierlist chapter in FTBQuests.
 */
public class WeaponTierlistGenerator extends AbstractTierlistGenerator<ItemData.WeaponData> {

    public WeaponTierlistGenerator(TierOverrideManager overrideManager) {
        super(overrideManager);
    }

    @Override
    protected String getChapterId() {
        return "autotierlist_weapons";
    }

    @Override
    protected String getChapterTitle() {
        return "Weapon Tierlist";
    }

    @Override
    protected String getItemTypeName() {
        return "weapons";
    }

    @Override
    protected void configureFilter(ItemFilter filter) {
        filter.loadWeaponTags(AutoTierlistConfig.WEAPON_TAGS.get());
        filter.loadWeaponItems(AutoTierlistConfig.WEAPON_ITEMS.get());
    }

    @Override
    protected List<ItemData.WeaponData> scanItems(ItemScanner scanner) {
        return scanner.scanWeapons();
    }

    @Override
    protected Map<Integer, List<TierCalculator.TieredItem<ItemData.WeaponData>>> assignTiers(
            TierCalculator calculator, List<ItemData.WeaponData> items) {
        return calculator.assignWeaponTiers(items);
    }

    @Override
    protected ResourceLocation getItemId(ItemData.WeaponData item) {
        return item.id();
    }

    @Override
    protected ItemStack getItemStack(ItemData.WeaponData item) {
        return item.stack();
    }

    @Override
    protected String getTierLabel(int tier) {
        double minDPS = tier * AutoTierlistConfig.tierMultiplier;
        double maxDPS = (tier + 1) * AutoTierlistConfig.tierMultiplier;
        return String.format("[%d] DPS: [%.1f-%.1f)", tier, minDPS, maxDPS);
    }

    @Override
    protected double getItemScore(ItemData.WeaponData item) {
        return item.getDPS();
    }
}
