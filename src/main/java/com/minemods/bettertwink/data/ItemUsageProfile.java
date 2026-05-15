package com.minemods.bettertwink.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Tracks how often a particular item type is accessed and where the player
 * most frequently deposits it.  Used by the scoring engine to boost sorting
 * accuracy over time (§1.1 of the design doc).
 *
 * <p>All time measurements are in <em>game ticks</em> (20 ticks = 1 second).
 */
public class ItemUsageProfile {

    /** Exponential moving average accesses-per-hour. Higher = frequently used. */
    public double accessFrequency = 0.0;

    /** Tick when this item was last accessed (taken or deposited). */
    public long lastAccessedTick = 0;

    /** Total recorded access events (for long-term averaging). */
    public int totalAccesses = 0;

    /** Rolling average of stack sizes during access. */
    public int avgStackSize = 1;

    /**
     * The chest position that the player has deposited this item into most often.
     * The sorting engine awards a +500 bonus when the candidate chest equals this.
     */
    public BlockPos preferredChest = null;

    /** Confidence counter for {@link #preferredChest}: increments on confirming deposit,
     *  decrements on conflicting deposit.  Reset to 0 if it reaches ≤ 0. */
    private int preferredVotes = 0;

    /** EMA decay: 0.85 → ~7 accesses half-life.  Lower = forgets faster. */
    private static final double DECAY = 0.85;
    private static final double TICKS_PER_HOUR = 20.0 * 3600.0;

    // ── Access recording ──────────────────────────────────────────────────

    /**
     * Record one access event (player taking or bot depositing the item).
     *
     * @param currentTick current game tick for frequency calculation
     * @param stackSize   count of items in the stack
     */
    public void recordAccess(long currentTick, int stackSize) {
        if (lastAccessedTick > 0 && currentTick > lastAccessedTick) {
            double elapsedHours = (currentTick - lastAccessedTick) / TICKS_PER_HOUR;
            if (elapsedHours > 0) {
                double instantFreq = 1.0 / Math.max(elapsedHours, 1e-5);
                accessFrequency = DECAY * accessFrequency + (1.0 - DECAY) * instantFreq;
            }
        }
        lastAccessedTick = currentTick;
        totalAccesses++;
        // running average of stack sizes
        avgStackSize = (avgStackSize * (totalAccesses - 1) + stackSize) / totalAccesses;
    }

    /**
     * Record that the player manually deposited this item into {@code chest}.
     * Updates {@link #preferredChest} via a vote-based algorithm that is robust
     * against temporary misplacement.
     */
    public void recordDeposit(BlockPos chest) {
        if (chest == null) return;
        if (preferredChest == null) {
            preferredChest = chest;
            preferredVotes = 1;
        } else if (preferredChest.equals(chest)) {
            preferredVotes = Math.min(preferredVotes + 1, 1000);
        } else {
            preferredVotes--;
            if (preferredVotes <= 0) {
                preferredChest = chest;
                preferredVotes = 1;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** @return true if accessed more than 5 times per hour on average. */
    public boolean isFrequentlyUsed() {
        return accessFrequency > 5.0;
    }

    /** @return true if accessed less than 0.2 times per hour (rarely used). */
    public boolean isRarelyUsed() {
        return totalAccesses >= 3 && accessFrequency < 0.2;
    }

    // ── Serialization ─────────────────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putDouble("Freq",      accessFrequency);
        t.putLong  ("LastTick",  lastAccessedTick);
        t.putInt   ("Total",     totalAccesses);
        t.putInt   ("AvgStack",  avgStackSize);
        if (preferredChest != null) {
            t.putInt("PX", preferredChest.getX());
            t.putInt("PY", preferredChest.getY());
            t.putInt("PZ", preferredChest.getZ());
            t.putInt("PVotes", preferredVotes);
        }
        return t;
    }

    public static ItemUsageProfile deserializeNBT(CompoundTag t) {
        ItemUsageProfile p    = new ItemUsageProfile();
        p.accessFrequency     = t.getDouble("Freq");
        p.lastAccessedTick    = t.getLong  ("LastTick");
        p.totalAccesses       = t.getInt   ("Total");
        p.avgStackSize        = t.contains ("AvgStack") ? t.getInt("AvgStack") : 1;
        if (t.contains("PX")) {
            p.preferredChest  = new BlockPos(t.getInt("PX"), t.getInt("PY"), t.getInt("PZ"));
            p.preferredVotes  = t.getInt("PVotes");
        }
        return p;
    }
}
