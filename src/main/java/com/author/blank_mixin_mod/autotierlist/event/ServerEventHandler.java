package com.author.blank_mixin_mod.autotierlist.event;

import com.author.blank_mixin_mod.autotierlist.command.AutoTierlistCommand;
import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import com.author.blank_mixin_mod.autotierlist.generation.TierlistGenerator;
import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Handles server lifecycle events for Auto-Tierlist.
 */
@Mod.EventBusSubscriber(modid = "blank_mixin_mod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Register commands when the server starts.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AutoTierlistCommand.register(event.getDispatcher());
        LOGGER.info("Auto-Tierlist commands registered");
    }

    /**
     * Auto-generate tierlists when the server starts (if enabled in config).
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Schedule generation on server thread to ensure all mods are loaded
        event.getServer().execute(() -> {
            if (AutoTierlistConfig.autoGenerateOnStart) {
                LOGGER.info("Auto-generating tierlists on server start...");
                try {
                    TierlistGenerator generator = new TierlistGenerator();
                    generator.generateAll(event.getServer());
                } catch (Exception e) {
                    LOGGER.error("Failed to auto-generate tierlists on server start", e);
                }
            } else {
                LOGGER.info("Auto-generation on server start is disabled in config");
                LOGGER.info("Use '/autotierlist generate' to manually generate tierlists");
            }
        });
    }
}
