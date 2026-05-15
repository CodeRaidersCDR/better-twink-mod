package com.minemods.bettertwink.client.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Lightweight statistics tracker for the sorting bot.
 *
 * <p>Tracked metrics:
 * <ul>
 *   <li>{@code itemsSortedTotal} — cumulative items successfully deposited.</li>
 *   <li>{@code itemsCraftedTotal} — cumulative items crafted.</li>
 *   <li>{@code sortSessionCount} — number of completed scan→sort cycles.</li>
 *   <li>{@code topItems} — top-10 most frequently sorted item IDs.</li>
 * </ul>
 *
 * <p>Session sub-counts ({@code sortedThisSession}) reset when {@link #resetSession()} is
 * called (typically on world disconnect), while totals persist via NBT.
 */
public class BotStats {

    private static BotStats INSTANCE;

    private long itemsSortedTotal  = 0;
    private long itemsCraftedTotal = 0;
    private int  sortSessionCount  = 0;
    private long sortedThisSession = 0;

    /** item id → sort count (unsorted; sorted on demand) */
    private final Map<String, Long> itemSortCounts = new LinkedHashMap<>();

    private BotStats() {}

    public static BotStats getInstance() {
        if (INSTANCE == null) INSTANCE = new BotStats();
        return INSTANCE;
    }

    // ── Record operations ─────────────────────────────────────────────────

    /**
     * Record one item stack successfully deposited by the bot.
     *
     * @param itemId    registry id string (e.g. "minecraft:cobblestone")
     * @param count     number of items in the stack
     */
    public void recordSort(String itemId, int count) {
        itemsSortedTotal  += count;
        sortedThisSession += count;
        itemSortCounts.merge(itemId, (long) count, Long::sum);
    }

    /**
     * Record a crafting operation completing.
     *
     * @param count number of items output
     */
    public void recordCraft(int count) {
        itemsCraftedTotal += count;
    }

    /** Mark one full scan→sort cycle as complete. */
    public void recordSessionComplete() {
        sortSessionCount++;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public long getItemsSortedTotal()    { return itemsSortedTotal; }
    public long getItemsCraftedTotal()   { return itemsCraftedTotal; }
    public int  getSortSessionCount()    { return sortSessionCount; }
    public long getSortedThisSession()   { return sortedThisSession; }

    /**
     * Returns the top-N most sorted item IDs in descending order of count.
     */
    public List<Map.Entry<String, Long>> getTopItems(int n) {
        return itemSortCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    /** Reset per-session counters (call on world disconnect or bot stop). */
    public void resetSession() {
        sortedThisSession = 0;
    }

    /** Full reset (call when player explicitly clears stats). */
    public void resetAll() {
        itemsSortedTotal  = 0;
        itemsCraftedTotal = 0;
        sortSessionCount  = 0;
        sortedThisSession = 0;
        itemSortCounts.clear();
    }

    // ── NBT persistence ───────────────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("itemsSortedTotal",  itemsSortedTotal);
        tag.putLong("itemsCraftedTotal", itemsCraftedTotal);
        tag.putInt ("sortSessionCount",  sortSessionCount);

        // top-20 items stored as "id|count" strings
        ListTag topTag = new ListTag();
        getTopItems(20).forEach(e -> topTag.add(StringTag.valueOf(e.getKey() + "|" + e.getValue())));
        tag.put("topItems", topTag);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        itemsSortedTotal  = tag.getLong("itemsSortedTotal");
        itemsCraftedTotal = tag.getLong("itemsCraftedTotal");
        sortSessionCount  = tag.getInt ("sortSessionCount");
        itemSortCounts.clear();
        ListTag topTag = tag.getList("topItems", Tag.TAG_STRING);
        for (int i = 0; i < topTag.size(); i++) {
            String entry = topTag.getString(i);
            int sep = entry.lastIndexOf('|');
            if (sep < 0) continue;
            try {
                String id    = entry.substring(0, sep);
                long   count = Long.parseLong(entry.substring(sep + 1));
                itemSortCounts.put(id, count);
            } catch (NumberFormatException ignored) {}
        }
    }
}
