package com.minemods.bettertwink.sorting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.minemods.bettertwink.sorting.ItemSortingEngine.ItemCategory;

import java.util.Comparator;

/**
 * FIX BUG #3: Comparator for ordering items inside a chest.
 * Supports NONE, DEDUPE_ONLY, GROUPED (by category then id), and ALPHABETICAL modes.
 */
public final class SortingComparator {

    public enum SortingMode { NONE, DEDUPE_ONLY, GROUPED, ALPHABETICAL }

    private SortingComparator() {}

    /**
     * Returns a comparator appropriate for the given sorting mode.
     * NONE / DEDUPE_ONLY → items remain in insertion order (no reordering).
     * GROUPED            → sorted by category ordinal, then registry id, then count desc.
     * ALPHABETICAL       → sorted by registry id, then count desc.
     */
    public static Comparator<ItemStack> forMode(SortingMode mode) {
        return switch (mode) {
            case GROUPED     -> byCategory().thenComparing(byId()).thenComparing(byCountDesc());
            case ALPHABETICAL -> byId().thenComparing(byCountDesc());
            default          -> (a, b) -> 0; // no reorder
        };
    }

    // ── Primitive comparators ─────────────────────────────────────────────────

    /** Sort by ItemCategory ordinal (FOOD < WEAPONS < ARMOR < … < MISC). */
    private static Comparator<ItemStack> byCategory() {
        return Comparator.comparingInt(
                (ItemStack s) -> ItemSortingEngine.getCategory(s).ordinal());
    }

    /** Sort lexicographically by full registry id ("minecraft:cobblestone"). */
    private static Comparator<ItemStack> byId() {
        return Comparator.comparing(
                s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
    }

    /** Sort by stack count descending (fullest stacks come first). */
    private static Comparator<ItemStack> byCountDesc() {
        return Comparator.comparingInt(ItemStack::getCount).reversed();
    }
}
