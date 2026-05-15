package com.minemods.bettertwink.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * Chooses the optimal fuel for furnace operations.
 *
 * <p>Fuel priority (from best to worst):
 * <ol>
 *   <li>coal_block (800 ticks / 8 items per coal)</li>
 *   <li>coal (200 ticks)</li>
 *   <li>charcoal (200 ticks)</li>
 *   <li>blaze_rod (120 ticks)</li>
 *   <li>Any wood log / plank (15–300 ticks)</li>
 * </ol>
 */
public final class FuelManager {

    private FuelManager() {}

    // Priority ordered: higher index = lower priority
    private static final List<String> FUEL_PRIORITY = List.of(
            "coal_block",       // best — 800 burn ticks
            "coal",             // 200 ticks
            "charcoal",         // 200 ticks
            "blaze_rod",        // 120 ticks
            "oak_log",          "birch_log",    "spruce_log",  "jungle_log",
            "acacia_log",       "dark_oak_log", "mangrove_log","cherry_log",
            "bamboo_block",     "oak_planks",   "birch_planks","spruce_planks",
            "jungle_planks",    "acacia_planks","dark_oak_planks",
            "oak_slab",         "birch_slab",   "spruce_slab",
            "stick",            // 5 ticks each — lowest priority
            "wooden_pickaxe",   "wooden_shovel","wooden_axe",  "wooden_hoe",
            "wooden_sword"
    );

    /**
     * From a list of available item stacks, returns the best fuel stack to use,
     * or {@link ItemStack#EMPTY} if none is usable fuel.
     */
    public static ItemStack getBestFuel(List<ItemStack> available) {
        if (available == null || available.isEmpty()) return ItemStack.EMPTY;

        return available.stream()
                .filter(s -> !s.isEmpty())
                .filter(s -> getFuelValue(s) > 0)
                .min(Comparator.comparingInt(s -> priorityOf(s))) // lower index = higher priority
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Returns the burn time (in ticks) for one unit of the given item, or 0 if not fuel.
     * Values match vanilla furnace behaviour.
     */
    public static int getFuelValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return switch (id) {
            case "coal_block"    -> 16000;
            case "coal",
                 "charcoal"      -> 1600;
            case "blaze_rod"     -> 2400;
            case "oak_log",    "birch_log",  "spruce_log",   "jungle_log",
                 "acacia_log", "dark_oak_log","mangrove_log","cherry_log" -> 300;
            case "bamboo_block"  -> 300;
            case "oak_planks",  "birch_planks", "spruce_planks",
                 "jungle_planks","acacia_planks","dark_oak_planks" -> 300;
            case "oak_slab",    "birch_slab",   "spruce_slab" -> 150;
            case "stick"         -> 100;
            case "wooden_pickaxe","wooden_shovel","wooden_axe",
                 "wooden_hoe",    "wooden_sword" -> 200;
            default -> 0;
        };
    }

    /** Returns the fuel's position in {@link #FUEL_PRIORITY} (lower = better). */
    private static int priorityOf(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int idx = FUEL_PRIORITY.indexOf(id);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }
}
