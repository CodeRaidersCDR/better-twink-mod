package com.minemods.bettertwink.sorting;

import net.minecraft.core.BlockPos;
import com.minemods.bettertwink.crafting.CraftRule;

/**
 * Sealed interface representing one atomic step the bot must perform.
 * The SortPlanner produces a list of these; the SortingBotController executes them.
 */
public sealed interface SortOp
        permits SortOp.Navigate, SortOp.OpenContainer, SortOp.Take,
                SortOp.Put, SortOp.Craft, SortOp.Wait, SortOp.Close {

    /** Navigate to the given block position. */
    record Navigate(BlockPos dest) implements SortOp {}

    /** Open a container at the given position (right-click). */
    record OpenContainer(BlockPos pos) implements SortOp {}

    /** Shift-click the item in the given container slot to take it. */
    record Take(int slot, String itemId) implements SortOp {}

    /** Shift-click the item in the given player-inventory slot to deposit it. */
    record Put(int slot, String itemId) implements SortOp {}

    /** Execute the given craft rule the specified number of times. */
    record Craft(CraftRule rule, int times) implements SortOp {}

    /** Wait the given number of ticks before proceeding. */
    record Wait(int ticks) implements SortOp {}

    /** Close the currently open container. */
    record Close() implements SortOp {}
}
