package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemData;
import com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemScanner;
import com.saloeater.ftbquests_tierlists.autotierlist.analysis.TierCalculator;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter;
import com.saloeater.ftbquests_tierlists.autotierlist.config.TierOverrideManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Generates the armor tierlist chapter in FTBQuests.
 */
public class ArmorTierlistGenerator extends AbstractTierlistGenerator<ItemData.ArmorData> {

    public ArmorTierlistGenerator(TierOverrideManager overrideManager) {
        super(overrideManager);
    }

    @Override
    protected String getChapterId() {
        return AutoTierlistConfig.ARMOR_CHAPTER_ID.get();
    }

    @Override
    protected MutableComponent getChapterTitle() {
        return Component.translatable(AutoTierlistConfig.ARMOR_CHAPTER_TITLE.get());
    }

    @Override
    protected String getItemTypeName() {
        return "armor";
    }

    @Override
    protected void configureFilter(ItemFilter filter) {
        filter.loadArmorTags(AutoTierlistConfig.ARMOR_TAGS.get());
        filter.loadArmorItems(AutoTierlistConfig.ARMOR_ITEMS.get());
    }

    @Override
    protected List<ItemData.ArmorData> scanItems(ItemScanner scanner) {
        return scanner.scanArmor();
    }

    @Override
    protected Map<Integer, List<TierCalculator.TieredItem<ItemData.ArmorData>>> assignTiers(
            TierCalculator calculator, List<ItemData.ArmorData> items) {
        return calculator.assignArmorTiers(items);
    }

    @Override
    protected ResourceLocation getItemId(ItemData.ArmorData item) {
        return item.id();
    }

    @Override
    protected ItemStack getItemStack(ItemData.ArmorData item) {
        return item.stack();
    }

    @Override
    protected String getTierLabel(int tier) {
        return String.format("[%d] Armor: %d", tier, tier);
    }

    @Override
    protected double getItemScore(ItemData.ArmorData item) {
        return item.getScore();
    }
}
