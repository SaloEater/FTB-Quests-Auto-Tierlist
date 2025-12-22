package com.saloeater.ftbquests_tierlists.autotierlist.event;

import com.saloeater.ftbquests_tierlists.Tierlists;
import com.saloeater.ftbquests_tierlists.autotierlist.command.AutoTierlistClientCommand;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import com.saloeater.ftbquests_tierlists.autotierlist.generation.TierlistGenerator;
import com.saloeater.ftbquests_tierlists.autotierlist.integration.EMIIntegration;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Handles client lifecycle events for Auto-Tierlist.
 */
@Mod.EventBusSubscriber(modid = "ftbquests_tierlists", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {
    /**
     * Register client commands.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AutoTierlistClientCommand.register(event.getDispatcher());
        Tierlists.LOGGER.info("Auto-Tierlist client commands registered");
    }
}
