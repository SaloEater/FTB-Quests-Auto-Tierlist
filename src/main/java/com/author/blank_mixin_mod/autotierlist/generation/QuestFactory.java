package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.mixin.QuestAccessor;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;

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
            StringBuilder subtitle = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                String value = list.get(i).getString();
                if (value.isEmpty()) {
                    continue;
                }
                subtitle.append(value);
                if (i < list.size() - 1) {
                    subtitle.append("\n");
                }
            }
            var subtitleCleared = subtitle.toString().replaceAll("\s&\s", " \\& ");
            quest.setRawSubtitle(subtitleCleared);
        }

        // Register quest
        quest.onCreated();

        return quest;
    }

    /**
     * Create a secret quest that marks a tier boundary.
     *
     * @param questFile The quest file
     * @param chapter The parent chapter
     * @param tier The tier number
     * @param y Y coordinate
     * @return The created quest
     */
    public static Quest createSecretTierQuest(ServerQuestFile questFile, Chapter chapter,
                                             int tier, double y) {
        return createSecretTierQuest(questFile, chapter, "Tier " + tier, y);
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
}
