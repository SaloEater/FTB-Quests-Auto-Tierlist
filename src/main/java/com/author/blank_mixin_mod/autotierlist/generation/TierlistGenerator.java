package com.author.blank_mixin_mod.autotierlist.generation;

import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import com.author.blank_mixin_mod.autotierlist.config.TierOverrideManager;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for generating both weapon and armor tierlists.
 */
public class TierlistGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String WEAPON_CHAPTER_ID = "autotierlist_weapons";
    private static final String ARMOR_CHAPTER_ID = "autotierlist_armor";

    /**
     * Generate all tierlists (weapons and armor).
     *
     * @param server The Minecraft server
     * @param enableProgressionAlignment Whether to enable progression-based alignment
     */
    public void generateAll(MinecraftServer server, boolean enableProgressionAlignment) {
        LOGGER.info("Starting Auto-Tierlist generation (progression: {})...", enableProgressionAlignment);

        try {
            // Get quest file
            ServerQuestFile questFile = ServerQuestFile.INSTANCE;
            if (questFile == null) {
                LOGGER.error("FTBQuests not loaded! Cannot generate tierlists.");
                LOGGER.error("Make sure FTBQuests mod is installed and loaded.");
                return;
            }

            // Load tier overrides from config
            TierOverrideManager overrideManager = new TierOverrideManager();
            overrideManager.loadWeaponOverrides(AutoTierlistConfig.WEAPON_TIER_OVERRIDES.get());
            overrideManager.loadArmorOverrides(AutoTierlistConfig.ARMOR_TIER_OVERRIDES.get());

            // Clean up existing chapters
            cleanupExistingChapters(questFile);

            // Generate weapon tierlist
            if (AutoTierlistConfig.ENABLE_WEAPON_TIERLIST.get()) {
                try {
                    LOGGER.info("Generating weapon tierlist...");
                    WeaponTierlistGenerator weaponGen = new WeaponTierlistGenerator(overrideManager);
                    weaponGen.generate(questFile, server.overworld(), enableProgressionAlignment);
                    LOGGER.info("Weapon tierlist generation complete");
                } catch (Exception e) {
                    LOGGER.error("Failed to generate weapon tierlist", e);
                }
            } else {
                LOGGER.info("Weapon tierlist generation disabled in config");
            }

            // Generate armor tierlist
            if (AutoTierlistConfig.ENABLE_ARMOR_TIERLIST.get()) {
                try {
                    LOGGER.info("Generating armor tierlist...");
                    ArmorTierlistGenerator armorGen = new ArmorTierlistGenerator(overrideManager);
                    armorGen.generate(questFile, server.overworld(), enableProgressionAlignment);
                    LOGGER.info("Armor tierlist generation complete");
                } catch (Exception e) {
                    LOGGER.error("Failed to generate armor tierlist", e);
                }
            } else {
                LOGGER.info("Armor tierlist generation disabled in config");
            }

            // Save changes - refresh ID map, clear cache, mark dirty
            questFile.refreshIDMap();
            questFile.clearCachedData();
            questFile.markDirty();

            // Force save to disk
            try {
                questFile.saveNow();
                questFile.load();
                LOGGER.info("Quest file saved successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to save quest file", e);
            }

            LOGGER.info("Auto-Tierlist generation complete!");

        } catch (Exception e) {
            LOGGER.error("Fatal error during tierlist generation", e);
        }
    }

    /**
     * Remove existing tierlist chapters to avoid duplicates.
     */
    private void cleanupExistingChapters(ServerQuestFile questFile) {
        List<Chapter> chaptersToRemove = new ArrayList<>();

        // Find existing tierlist chapters
        for (Chapter chapter : questFile.getAllChapters()) {
            String filename = chapter.getFilename();
            if (WEAPON_CHAPTER_ID.equals(filename) || ARMOR_CHAPTER_ID.equals(filename)) {
                chaptersToRemove.add(chapter);
                LOGGER.info("Found existing tierlist chapter to remove: {} (ID: {})", filename, chapter.id);
            }
        }

        if (chaptersToRemove.isEmpty()) {
            LOGGER.info("No existing tierlist chapters found to clean up");
            return;
        }

        LOGGER.info("Removing {} existing tierlist chapter(s)...", chaptersToRemove.size());

        // Remove them
        for (Chapter chapter : chaptersToRemove) {
            try {
                String filename = chapter.getFilename();
                LOGGER.debug("Deleting children of chapter: {}", filename);
                chapter.deleteChildren();

                LOGGER.debug("Deleting chapter: {}", filename);
                chapter.deleteSelf();

                LOGGER.info("Successfully removed chapter: {}", filename);
                ClientQuestFile.INSTANCE.deleteObject(chapter.id);
            } catch (Exception e) {
                LOGGER.error("Failed to remove chapter {}: {}", chapter.getFilename(), e.getMessage(), e);
            }
        }

        // Refresh quest file state after deletions
        LOGGER.debug("Refreshing quest file after cleanup...");
        questFile.refreshIDMap();
        questFile.clearCachedData();
        questFile.markDirty();

        // Force save after cleanup
        try {
            questFile.saveNow();
            questFile.load();
            LOGGER.info("Quest file saved after cleanup");
        } catch (Exception e) {
            LOGGER.error("Failed to save quest file after cleanup", e);
        }
    }

    /**
     * Clear all generated tierlist chapters.
     */
    public void clearAll(MinecraftServer server) {
        LOGGER.info("=== Starting tierlist clear operation ===");

        try {
            ServerQuestFile questFile = ServerQuestFile.INSTANCE;
            if (questFile == null) {
                LOGGER.error("FTBQuests not loaded!");
                return;
            }

            // Cleanup handles its own save now
            cleanupExistingChapters(questFile);

            LOGGER.info("=== Tierlist clear operation complete ===");

        } catch (Exception e) {
            LOGGER.error("Error clearing tierlists", e);
        }
    }
}
