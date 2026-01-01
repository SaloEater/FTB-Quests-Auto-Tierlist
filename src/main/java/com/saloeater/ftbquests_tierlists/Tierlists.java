package com.saloeater.ftbquests_tierlists;

import com.mojang.logging.LogUtils;
import com.saloeater.ftbquests_tierlists.autotierlist.config.AutoTierlistConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Tierlists.MODID)
public class Tierlists
{
    public static final String MODID = "ftbquests_tierlists";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Tierlists(FMLJavaModLoadingContext context)
    {
        var forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);

        // Register Auto-Tierlist common config
        context.registerConfig(ModConfig.Type.COMMON, AutoTierlistConfig.SPEC);

        Tierlists.LOGGER.info("Auto-Tierlist module loaded (server-side)");
    }
}
