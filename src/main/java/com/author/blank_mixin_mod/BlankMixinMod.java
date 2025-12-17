package com.author.blank_mixin_mod;

import com.mojang.logging.LogUtils;
import com.author.blank_mixin_mod.autotierlist.config.AutoTierlistConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BlankMixinMod.MODID)
public class BlankMixinMod
{
    public static final String MODID = "blank_mixin_mod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlankMixinMod(FMLJavaModLoadingContext context)
    {
        var forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);

        // Register Auto-Tierlist config
        context.registerConfig(ModConfig.Type.COMMON, AutoTierlistConfig.SPEC);

        LOGGER.info("Auto-Tierlist module loaded");
    }
}
