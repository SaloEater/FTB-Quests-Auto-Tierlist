package com.saloeater.ftbquests_tierlists.autotierlist.event;

import com.saloeater.ftbquests_tierlists.Tierlists;
import com.saloeater.ftbquests_tierlists.autotierlist.command.AutoTierlistServerCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles server lifecycle events for Auto-Tierlist.
 */
@Mod.EventBusSubscriber(modid = "ftbquests_tierlists", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    /**
     * Register server commands.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AutoTierlistServerCommand.register(event.getDispatcher());
        Tierlists.LOGGER.info("Auto-Tierlist server commands registered");
    }
}
