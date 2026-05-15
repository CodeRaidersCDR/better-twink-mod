package com.minemods.bettertwink;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.minemods.bettertwink.config.BetterTwinkConfig;
import org.slf4j.Logger;

@Mod(BetterTwinkMod.MOD_ID)
public class BetterTwinkMod {
    public static final String MOD_ID = "bettertwink";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BetterTwinkMod() {
        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BetterTwinkConfig.SPEC);
        
        LOGGER.info("Better Twink mod initialized");
    }
}
