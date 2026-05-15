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

    // §4.1 Humanisation profile (CASUAL / GRINDER / SPEEDRUNNER / AFK_LIKE / INSTANT)
    public static final ForgeConfigSpec.ConfigValue<String> HUMAN_PROFILE;
    // §4.4 Anti-cheat adaptation hint (NONE / GRIM / VULCAN / NCP)
    public static final ForgeConfigSpec.ConfigValue<String> ANTI_CHEAT_MODE;
    // §4.4 Max clicks per second — enforced on top of humanProfile delays
    public static final ForgeConfigSpec.IntValue MAX_CPS;

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

        HUMAN_PROFILE = BUILDER
                .comment("Humanisation profile controlling click timing (CASUAL / GRINDER / SPEEDRUNNER / AFK_LIKE / INSTANT)")
                .define("humanProfile", "CASUAL");

        ANTI_CHEAT_MODE = BUILDER
                .comment("Anti-cheat mode hint (NONE / GRIM / VULCAN / NCP). Tightens delay variance.")
                .define("antiCheatMode", "NONE");

        MAX_CPS = BUILDER
                .comment("Maximum clicks per second enforced regardless of profile (1–20)")
                .defineInRange("maxCps", 10, 1, 20);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
