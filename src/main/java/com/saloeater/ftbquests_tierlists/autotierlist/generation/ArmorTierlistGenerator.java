package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemData;
import com.saloeater.ftbquests_tierlists.autotierlist.analysis.ItemScanner;
import com.saloeater.ftbquests_tierlists.autotierlist.analysis.TierCalculator;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.config.ItemFilter;
import com.saloeater.ftbquests_tierlists.autotierlist.config.TierOverrideManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Generates the armor tierlist chapter in FTBQuests.
 */
public class ArmorTierlistGenerator extends AbstractTierlistGenerator<ItemData.ArmorData> {

    // Whether the current run moves magic armor into its own section (crafting mode only)
    private boolean separateMagicArmor = false;

    public ArmorTierlistGenerator(TierOverrideManager overrideManager) {
        super(overrideManager);
    }

    @Override
    public void generate(ServerQuestFile questFile, ServerLevel level, boolean enableProgressionAlignment, ResourceLocation chapterIconItemId) {
        separateMagicArmor = enableProgressionAlignment && AutoTierlistConfig.SEPARATE_MAGIC_ARMOR.get();
        super.generate(questFile, level, enableProgressionAlignment, chapterIconItemId);
    }

    /**
     * Whether this armor goes to the magic section instead of the regular armor rows.
     */
    private boolean inMagicSection(ItemData.ArmorData item) {
        return separateMagicArmor && item.isMagic();
    }

    @Override
    protected String getChapterId() {
        return AutoTierlistConfig.ARMOR_CHAPTER_ID.get();
    }

    @Override
    protected String getChapterTitle() {
        return AutoTierlistConfig.ARMOR_CHAPTER_TITLE.get();
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
        return calculator.assignArmorTiers(items, this::inMagicSection, this::getItemScore);
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
        if (tier >= TierCalculator.MAGIC_TIER_OFFSET) {
            int magicTier = tier - TierCalculator.MAGIC_TIER_OFFSET;
            return String.format("[%d] Magic: %d", magicTier, magicTier);
        }
        return String.format("[%d] Armor: %d", tier, tier);
    }

    @Override
    protected int getSection(ItemData.ArmorData item) {
        return inMagicSection(item) ? 1 : 0;
    }

    @Override
    protected double getExtraSpacingBeforeTier(int previousTier, int tier) {
        // Leave an empty row between the regular armor rows and the magic section
        boolean startsMagicSection = tier >= TierCalculator.MAGIC_TIER_OFFSET
            && previousTier < TierCalculator.MAGIC_TIER_OFFSET;
        return startsMagicSection
            ? AutoTierlistConfig.QUEST_SPACING_Y.get() + AutoTierlistConfig.TIER_SPACING_Y.get()
            : 0;
    }

    @Override
    protected double getItemScore(ItemData.ArmorData item) {
        if (inMagicSection(item)) {
            return item.getMagicScore(
                AutoTierlistConfig.MAGIC_SPELL_POWER_WEIGHT.get(),
                AutoTierlistConfig.MAGIC_MANA_WEIGHT.get(),
                AutoTierlistConfig.MAGIC_ARMOR_WEIGHT.get());
        }
        return item.getScore();
    }
}
