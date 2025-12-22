package com.saloeater.ftbquests_tierlists.autotierlist.generation;

import com.saloeater.ftbquests_tierlists.Tierlists;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.config.TierOverrideManager;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for generating both weapon and armor tierlists.
 */
public class TierlistGenerator {
    /**
     * Generate all tierlists (weapons and armor).
     *
     * @param server The Minecraft server
     */
    public void generateAll(MinecraftServer server) {
        try {
            // Get quest file
            ServerQuestFile questFile = ServerQuestFile.INSTANCE;
            if (questFile == null) {
                Tierlists.LOGGER.error("FTBQuests not loaded! Cannot generate tierlists.");
                Tierlists.LOGGER.error("Make sure FTBQuests mod is installed and loaded.");
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
                    Tierlists.LOGGER.info("Generating weapon tierlist...");
                    WeaponTierlistGenerator weaponGen = new WeaponTierlistGenerator(overrideManager);
                    weaponGen.generate(questFile, server.overworld(), true, AutoTierlistConfig.GetWeaponChapterIcon());
                    weaponGen.generate(questFile, server.overworld(), false, AutoTierlistConfig.GetWeaponChapterIcon());
                    Tierlists.LOGGER.info("Weapon tierlist generation complete");
                } catch (Exception e) {
                    Tierlists.LOGGER.error("Failed to generate weapon tierlist", e);
                }
            } else {
                Tierlists.LOGGER.info("Weapon tierlist generation disabled in config");
            }

            // Generate armor tierlist
            if (AutoTierlistConfig.ENABLE_ARMOR_TIERLIST.get()) {
                try {
                    Tierlists.LOGGER.info("Generating armor tierlist...");
                    ArmorTierlistGenerator armorGen = new ArmorTierlistGenerator(overrideManager);
                    armorGen.generate(questFile, server.overworld(), true, AutoTierlistConfig.GetArmorChapterIcon());
                    armorGen.generate(questFile, server.overworld(), false, AutoTierlistConfig.GetArmorChapterIcon());
                    Tierlists.LOGGER.info("Armor tierlist generation complete");
                } catch (Exception e) {
                    Tierlists.LOGGER.error("Failed to generate armor tierlist", e);
                }
            } else {
                Tierlists.LOGGER.info("Armor tierlist generation disabled in config");
            }

            // Save changes - refresh ID map, clear cache, mark dirty
            questFile.refreshIDMap();
            questFile.clearCachedData();
            questFile.markDirty();

            // Force save to disk
            try {
                questFile.saveNow();
                questFile.load();
                Tierlists.LOGGER.info("Quest file saved successfully");
            } catch (Exception e) {
                Tierlists.LOGGER.error("Failed to save quest file", e);
            }

            Tierlists.LOGGER.info("Auto-Tierlist generation complete!");

        } catch (Exception e) {
            Tierlists.LOGGER.error("Fatal error during tierlist generation", e);
        }
    }

    /**
     * Remove existing tierlist chapters to avoid duplicates.
     */
    private void cleanupExistingChapters(ServerQuestFile questFile) {
        List<Chapter> chaptersToRemove = new ArrayList<>();

        String weaponChapterId = AutoTierlistConfig.WEAPON_CHAPTER_ID.get();
        String armorChapterId = AutoTierlistConfig.ARMOR_CHAPTER_ID.get();

        // Find existing tierlist chapters
        for (Chapter chapter : questFile.getAllChapters()) {
            String filename = chapter.getFilename();
            if (weaponChapterId.contains(filename) || armorChapterId.contains(filename)) {
                chaptersToRemove.add(chapter);
                Tierlists.LOGGER.info("Found existing tierlist chapter to remove: {} (ID: {})", filename, chapter.id);
            }
        }

        if (chaptersToRemove.isEmpty()) {
            Tierlists.LOGGER.info("No existing tierlist chapters found to clean up");
            return;
        }

        Tierlists.LOGGER.info("Removing {} existing tierlist chapter(s)...", chaptersToRemove.size());

        // Remove them
        for (Chapter chapter : chaptersToRemove) {
            try {
                String filename = chapter.getFilename();
                Tierlists.LOGGER.debug("Deleting children of chapter: {}", filename);
                chapter.deleteChildren();

                Tierlists.LOGGER.debug("Deleting chapter: {}", filename);
                chapter.deleteSelf();

                Tierlists.LOGGER.info("Successfully removed chapter: {}", filename);
                ClientQuestFile.INSTANCE.deleteObject(chapter.id);
            } catch (Exception e) {
                Tierlists.LOGGER.error("Failed to remove chapter {}: {}", chapter.getFilename(), e.getMessage(), e);
            }
        }

        // Refresh quest file state after deletions
        Tierlists.LOGGER.debug("Refreshing quest file after cleanup...");
        questFile.refreshIDMap();
        questFile.clearCachedData();
        questFile.markDirty();

        // Force save after cleanup
        try {
            questFile.saveNow();
            questFile.load();
            Tierlists.LOGGER.info("Quest file saved after cleanup");
        } catch (Exception e) {
            Tierlists.LOGGER.error("Failed to save quest file after cleanup", e);
        }
    }

    /**
     * Clear all generated tierlist chapters.
     */
    public void clearAll(MinecraftServer server) {
        Tierlists.LOGGER.info("=== Starting tierlist clear operation ===");

        try {
            ServerQuestFile questFile = ServerQuestFile.INSTANCE;
            if (questFile == null) {
                Tierlists.LOGGER.error("FTBQuests not loaded!");
                return;
            }

            // Cleanup handles its own save now
            cleanupExistingChapters(questFile);

            Tierlists.LOGGER.info("=== Tierlist clear operation complete ===");

        } catch (Exception e) {
            Tierlists.LOGGER.error("Error clearing tierlists", e);
        }
    }
}
