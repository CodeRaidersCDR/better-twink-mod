package com.minemods.bettertwink.sorting;

import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.sorting.ItemSortingEngine.ItemCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Planning utility: builds a source→(target→itemIds) batch map using score-based routing.
 *
 * <p>Also updates {@code pinnedCategory} (first-scan, never overwritten) and
 * {@code cachedContents}/{@code cachedFreeSlots} on all {@link ChestConfiguration}s.
 *
 * <p>Source chests are ordered by TSP nearest-neighbour from the bot's position.
 */
public final class SortPlanner {

    private SortPlanner() {}

    /**
     * Produces a source → (target → itemIds) map for the current world state.
     *
     * @param scannedInventories chest contents keyed by position (may include QD chests)
     * @param configByPos        configured chests
     * @param quickDropChests    positions of QuickDrop chests (always fully drained)
     * @param botPos             current bot block position (for distance penalty in scoring)
     * @return ordered map (nearest source first) ready to be converted into batches
     */
    public static Map<BlockPos, Map<BlockPos, List<String>>> buildPlan(
            Map<BlockPos, List<ItemStack>> scannedInventories,
            Map<BlockPos, ChestConfiguration> configByPos,
            Set<BlockPos> quickDropChests,
            BlockPos botPos) {

        // ── 1. Update cached contents & free slots for every scanned chest ──────
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            ChestConfiguration cfg = configByPos.get(e.getKey());
            if (cfg == null) continue;
            // Assume single-chest (27 slots) or double-chest (54 slots) based on item count
            int totalSlots = e.getValue().size() > 27 ? 54 : 27;
            cfg.updateCache(e.getValue(), totalSlots);
        }

        // ── 2. Pin category for non-QD chests that have none yet ────────────────
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            BlockPos pos = e.getKey();
            if (quickDropChests.contains(pos)) continue;
            ChestConfiguration cfg = configByPos.get(pos);
            if (cfg == null || cfg.getPinnedCategory() != null) continue;
            List<ItemStack> items = e.getValue();
            if (items.isEmpty()) continue;
            Map<ItemCategory, Integer> scores = new EnumMap<>(ItemCategory.class);
            for (ItemStack s : items) scores.merge(ItemSortingEngine.getCategory(s), s.getCount(), Integer::sum);
            ItemCategory dominant = scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(ItemCategory.MISC);
            cfg.setPinnedCategory(dominant);
        }

        // ── 3. Route items ───────────────────────────────────────────────────────
        Map<BlockPos, Map<BlockPos, List<String>>> batchData = new LinkedHashMap<>();

        // 3a. QuickDrop chests: route every item to its best target
        for (BlockPos qd : quickDropChests) {
            for (ItemStack stack : scannedInventories.getOrDefault(qd, List.of())) {
                if (stack.isEmpty()) continue;
                BlockPos target = ItemSortingEngine.findBestChest(stack, configByPos, botPos);
                if (target == null || target.equals(qd)) continue;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                batchData.computeIfAbsent(qd, k -> new LinkedHashMap<>())
                         .computeIfAbsent(target, k -> new ArrayList<>())
                         .add(id);
            }
        }

        // 3b. Non-QD chests: route items the chest cannot store
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            BlockPos src = e.getKey();
            if (quickDropChests.contains(src)) continue;
            ChestConfiguration srcCfg = configByPos.get(src);
            if (srcCfg == null) continue;
            for (ItemStack stack : e.getValue()) {
                if (stack.isEmpty() || srcCfg.canStoreItem(stack)) continue;
                BlockPos target = ItemSortingEngine.findBestChest(stack, configByPos, botPos);
                if (target == null || target.equals(src)) continue;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                batchData.computeIfAbsent(src, k -> new LinkedHashMap<>())
                         .computeIfAbsent(target, k -> new ArrayList<>())
                         .add(id);
            }
        }

        // ── 4. Order sources by TSP nearest-neighbour ────────────────────────────
        return tspOrder(batchData, botPos);
    }

    /**
     * Async wrapper around {@link #buildPlan} that runs on the common ForkJoinPool.
     *
     * <p>The caller must pass <em>defensive copies</em> of all mutable collections
     * before calling this method to prevent data races with the main thread.
     */
    public static CompletableFuture<Map<BlockPos, Map<BlockPos, List<String>>>> buildPlanAsync(
            Map<BlockPos, List<ItemStack>> scannedInventories,
            Map<BlockPos, ChestConfiguration> configByPos,
            Set<BlockPos> quickDropChests,
            BlockPos botPos) {
        return CompletableFuture.supplyAsync(
                () -> buildPlan(scannedInventories, configByPos, quickDropChests, botPos),
                ForkJoinPool.commonPool());
    }

    /**
     * Reorders {@code batchData} entries using a greedy nearest-neighbour TSP
     * starting from {@code start}.
     */
    private static Map<BlockPos, Map<BlockPos, List<String>>> tspOrder(
            Map<BlockPos, Map<BlockPos, List<String>>> batchData, BlockPos start) {
        if (batchData.size() <= 1) return batchData;
        Map<BlockPos, Map<BlockPos, List<String>>> result = new LinkedHashMap<>();
        Set<BlockPos> remaining = new LinkedHashSet<>(batchData.keySet());
        BlockPos current = start != null ? start : BlockPos.ZERO;
        while (!remaining.isEmpty()) {
            final BlockPos cur = current;
            BlockPos nearest = remaining.stream()
                    .min(Comparator.comparingDouble(p -> p.distSqr(cur)))
                    .orElseThrow();
            result.put(nearest, batchData.get(nearest));
            remaining.remove(nearest);
            current = nearest;
        }
        return result;
    }
}
