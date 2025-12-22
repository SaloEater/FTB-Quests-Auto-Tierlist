package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.Tierlists;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.github.elenterius.biomancy.tooltip.EmptyLineTooltipComponent;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.ConfigWithVariants;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.AdvancementTask;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating FTBQuests objects.
 */
public class QuestFactory {

    /**
     * Create a quest that displays an item.
     *
     * @param questFile The quest file
     * @param chapter The parent chapter
     * @param item The item to display
     * @param x X coordinate
     * @param y Y coordinate
     * @return The created quest
     */
    public static Quest createItemQuest(ServerQuestFile questFile, Chapter chapter,
                                       ItemStack item, double x, double y) {
        // Create quest
        long questId = questFile.newID();
        Quest quest = new Quest(questId, chapter);

        // Set position
        quest.setX(x);
        quest.setY(y);

        // Create item task (display only, not consumable)
        long taskId = questFile.newID();
        ItemTask task = new ItemTask(taskId, quest);
        task.setStackAndCount(item, 1);
        task.setConsumeItems(dev.ftb.mods.ftblibrary.config.Tristate.FALSE);
        task.onCreated();
        var list = new ArrayList<Component>();
        item.getItem().appendHoverText(item, null, list, TooltipFlag.NORMAL);
        if (!list.isEmpty()) {
            MutableComponent subtitle = null;
            for (int i = 0; i < list.size(); i++) {
                Component component = list.get(i);
                if (!isValidComponent(component)) {
                    continue;
                }
                if (subtitle == null) {
                    subtitle = (MutableComponent) component;
                } else {
                    subtitle = subtitle.append(component);
                }
                if (i < list.size() - 1) {
                    subtitle = subtitle.append(Component.literal("\n"));
                }
            }
            try {
                quest.setRawSubtitle(Component.Serializer.toJson(subtitle));
            } catch (Exception e) {
                LogUtils.getLogger().error("Failed to set subtitle for item {}: {}", item.toString(), e.getMessage());
            }
        }

        List<AutoTierlistConfig.TagEntry> tagEntries = AutoTierlistConfig.getArmageddonTagEntries();
        for (AutoTierlistConfig.TagEntry entry : tagEntries) {
            for (var tagKey : entry.getTagKeys()) {
                if (item.is(tagKey)) {
                    var tier = Component.literal("[").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(entry.getLabel())).withStyle(ChatFormatting.getByCode(entry.getColor())))
                            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                            .append(item.getHoverName());
                    quest.setRawTitle(Component.Serializer.toJson(tier));
                }
            }
        }


        // Register quest
        quest.onCreated();

        return quest;
    }

    private static boolean isValidComponent(Component component) {
        return component.getContents() != ComponentContents.EMPTY && !(component instanceof EmptyLineTooltipComponent) && !(component.getContents() instanceof EmptyLineTooltipComponent);
    }

    /**
     * Create a secret quest that marks a tier boundary with a custom title.
     *
     * @param questFile The quest file
     * @param chapter The parent chapter
     * @param title The title to display
     * @param y Y coordinate
     * @return The created quest
     */
    public static Quest createSecretTierQuest(ServerQuestFile questFile, Chapter chapter,
                                             String title, double y) {
        // Create quest
        long questId = questFile.newID();
        Quest quest = new Quest(questId, chapter);

        // Set title and position
        quest.setRawTitle(title);
        quest.setX(-2.0); // Far left of the grid
        quest.setY(y);
        //((QuestAccessor) quest).setInvisibleUntilCompleted(true);

        // Create dummy checkmark task
        long taskId = questFile.newID();
        CheckmarkTask task = new CheckmarkTask(taskId, quest);
        task.onCreated();

        // Register quest
        quest.onCreated();

        return quest;
    }

    /**
     * Calculate the Y coordinate for a tier's base position.
     *
     * @param tierIndex The sequential index of this tier (0 for first tier, 1 for second, etc.)
     * @param rowsPerTier Number of rows per tier
     * @param questSpacingY Vertical spacing between quests
     * @param tierSpacingY Extra spacing between tiers
     * @return The Y coordinate
     */
    public static double calculateTierBaseY(int tierIndex, int rowsPerTier,
                                           double questSpacingY, double tierSpacingY) {
        return tierIndex * (rowsPerTier * questSpacingY + tierSpacingY);
    }

    /**
     * Calculate the Y coordinate for a quest within a tier.
     *
     * @param tierBaseY The tier's base Y coordinate
     * @param row The row within the tier (0 = bottom)
     * @param questSpacingY Vertical spacing between quests
     * @return The Y coordinate
     */
    public static double calculateQuestY(double tierBaseY, int row, double questSpacingY) {
        return tierBaseY + row * questSpacingY;
    }

    /**
     * Create a header quest for a tag group with a translatable title and advancement task.
     *
     * @param questFile The quest file
     * @param chapter The parent chapter
     * @param itemId The item ID to use for the icon
     * @param advancementId The translatable title key
     * @param x X coordinate
     * @param y Y coordinate
     * @return The created quest
     */
    public static Quest createHeaderQuest(ServerQuestFile questFile, Chapter chapter,
                                          ResourceLocation itemId, ResourceLocation advancementId,
                                          double x, double y) {
        // Create quest
        long questId = questFile.newID();
        Quest quest = new Quest(questId, chapter);

        // Set position
        quest.setX(x);
        quest.setY(y);

        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        var advancement = getAdvancement(advancementId);
        if (advancement != null) {
            quest.setRawTitle(Component.Serializer.toJson(advancement.getDisplay().getTitle()));
            quest.setRawSubtitle(Component.Serializer.toJson(advancement.getDisplay().getDescription()));
        }

        long taskId = questFile.newID();
        AdvancementTask task = new AdvancementTask(taskId, quest);

        // Set advancement and rawIcon using ConfigGroup
        ConfigGroup taskConfigGroup = new ConfigGroup("");
        task.fillConfigGroup(taskConfigGroup);

        for (var value : taskConfigGroup.getValues()) {
            if (value.id.equals("advancement")) {
                ConfigWithVariants<ResourceLocation> advVal = (ConfigWithVariants<ResourceLocation>) value;
                advVal.setCurrentValue(advancementId);
                advVal.applyValue();
            }
        }

        if (item != null) {
            ItemStack stack = new ItemStack(item);
            task.setRawIcon(stack);
        }

        task.onCreated();

        // Set quest size using ConfigGroup
        ConfigGroup group = new ConfigGroup("");
        quest.fillConfigGroup(group);

        for (var value : group.getOrCreateSubgroup("appearance").getValues()) {
            if (value.id.equals("size")) {
                ConfigValue<Double> sizeVal = (ConfigValue<Double>) value;
                sizeVal.setCurrentValue(3.0);
                sizeVal.applyValue();
            }
        }

        // Register quest
        quest.onCreated();

        return quest;
    }

    private static Advancement getAdvancement(ResourceLocation advancementId) {
        ClientAdvancements advancements = Minecraft.getInstance().player.connection.getAdvancements();
        return advancements.getAdvancements().get(advancementId);
    }
}
