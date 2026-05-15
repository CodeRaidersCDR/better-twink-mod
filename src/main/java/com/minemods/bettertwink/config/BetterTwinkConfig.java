package com.minemods.bettertwink.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class BetterTwinkConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Client settings
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue ITEM_TRANSFER_DELAY;
    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_CHESTS;
    public static final ForgeConfigSpec.BooleanValue AUTO_SORT_ENABLED;

    static {
        BUILDER.push("general");
        
        ENABLED = BUILDER
                .comment("Enable Better Twink mod")
                .define("enabled", true);
        
        ITEM_TRANSFER_DELAY = BUILDER
                .comment("Delay in milliseconds between item transfers (for humanized movement, min 50)")
                .defineInRange("itemTransferDelay", 100, 50, 1000);
        
        HIGHLIGHT_CHESTS = BUILDER
                .comment("Highlight selected chests")
                .define("highlightChests", true);
        
        AUTO_SORT_ENABLED = BUILDER
                .comment("Enable automatic sorting")
                .define("autoSortEnabled", false);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
