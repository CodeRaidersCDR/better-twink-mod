package com.minemods.bettertwink.sorting;

import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.sorting.ItemSortingEngine.ItemCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
 *
 * <p>FIX BUG #1: QuickDrop chests AND dirty STORAGE chests are routed as sources.
 * FIX BUG #2: {@link #planConsolidation} merges scattered item stacks into one chest.
 * FIX BUG #4: Scoring uses +800 existing-item bonus (verified in ItemSortingEngine.scoreChest).
 */
public final class SortPlanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SortPlanner() {}

    /**
     * Produces a source → (target → itemIds) map for the current world state.
     *
     * @param scannedInventories chest contents keyed by position (may include QD chests)
     * @param configByPos        configured chests
     * @param quickDropChests    positions of QuickDrop chests (always fully drained)
     * @param botPos             current bot block position (for distance penalty in scoring)
     * @param currentTick        current game tick (for locked-chest filtering, BUG #12)
     * @return ordered map (nearest source first) ready to be converted into batches
     */
    public static Map<BlockPos, Map<BlockPos, List<String>>> buildPlan(
            Map<BlockPos, List<ItemStack>> scannedInventories,
            Map<BlockPos, ChestConfiguration> configByPos,
            Set<BlockPos> quickDropChests,
            BlockPos botPos,
            long currentTick) {

        // ── 1. Update cached contents & free slots for every scanned chest ──────
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            ChestConfiguration cfg = configByPos.get(e.getKey());
            if (cfg == null) continue;
            // FIX BUG #9: double-chest has 54 slots, single has 27
            int totalSlots = cfg.isDouble() ? 54 : (e.getValue().size() > 27 ? 54 : 27);
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

        // ── 3. Route items (FIX BUG #1: QD + dirty STORAGE, FIX BUG #12: skip locked)
        Map<BlockPos, Map<BlockPos, List<String>>> batchData = new LinkedHashMap<>();

        int qdCount = quickDropChests.size();
        LOGGER.info("[SortPlanner] PLANNING: {} chests, {} total stacks, quickDrop={}",
                scannedInventories.size(),
                scannedInventories.values().stream().mapToInt(List::size).sum(),
                qdCount);

        // 3a. FIX BUG #1: QuickDrop chests — route EVERY item to its best target
        for (BlockPos qd : quickDropChests) {
            // FIX BUG #12: skip if locked (player is using it)
            ChestConfiguration qdCfg = configByPos.get(qd);
            if (qdCfg != null && qdCfg.getLockedUntilTick() > currentTick) continue;

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

        // 3b. FIX BUG #1: Non-QD chests — route MISPLACED items (canStoreItem=false)
        //     Also covers STORAGE chests marked dirty=true after manual edits.
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            BlockPos src = e.getKey();
            if (quickDropChests.contains(src)) continue;
            ChestConfiguration srcCfg = configByPos.get(src);
            if (srcCfg == null) continue;
            // FIX BUG #12: skip locked chests
            if (srcCfg.getLockedUntilTick() > currentTick) continue;

            for (ItemStack stack : e.getValue()) {
                if (stack.isEmpty() || srcCfg.canStoreItem(stack)) continue;
                BlockPos target = ItemSortingEngine.findBestChest(stack, configByPos, botPos);
                if (target == null || target.equals(src)) continue;
                // FIX BUG #12: also skip if target is locked
                ChestConfiguration tgtCfg = configByPos.get(target);
                if (tgtCfg != null && tgtCfg.getLockedUntilTick() > currentTick) continue;
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
     * Backward-compatible overload (passes currentTick=0, disabling lock filtering).
     */
    public static Map<BlockPos, Map<BlockPos, List<String>>> buildPlan(
            Map<BlockPos, List<ItemStack>> scannedInventories,
            Map<BlockPos, ChestConfiguration> configByPos,
            Set<BlockPos> quickDropChests,
            BlockPos botPos) {
        return buildPlan(scannedInventories, configByPos, quickDropChests, botPos, 0L);
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
            BlockPos botPos,
            long currentTick) {
        return CompletableFuture.supplyAsync(
                () -> buildPlan(scannedInventories, configByPos, quickDropChests, botPos, currentTick),
                ForkJoinPool.commonPool());
    }

    /**
     * Backward-compatible async overload (passes currentTick=0).
     */
    public static CompletableFuture<Map<BlockPos, Map<BlockPos, List<String>>>> buildPlanAsync(
            Map<BlockPos, List<ItemStack>> scannedInventories,
            Map<BlockPos, ChestConfiguration> configByPos,
            Set<BlockPos> quickDropChests,
            BlockPos botPos) {
        return buildPlanAsync(scannedInventories, configByPos, quickDropChests, botPos, 0L);
    }

    // ── FIX BUG #2: Consolidation phase ──────────────────────────────────────

    /**
     * FIX BUG #2: Scans all STORAGE chests for the same item type scattered across
     * multiple chests and returns move tasks to consolidate each type into one "primary"
     * chest (the one with the highest score for that item).
     *
     * <p>This is called separately from {@link #buildPlan}; the caller appends the
     * resulting tasks to the batch queue.
     *
     * @param configByPos  configured chests (with up-to-date cachedContents)
     * @param botPos       bot position for scoring
     * @return list of (src→dst) move tasks expressed as (srcPos, srcItemId, dstPos)
     */
    public static List<ConsolidationTask> planConsolidation(
            Map<BlockPos, ChestConfiguration> configByPos,
            BlockPos botPos) {

        // FIX BUG #10: use ItemKey.strict so enchanted items don't get merged with plain ones
        Map<ItemKey, List<LocatedStack>> globalIndex = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, ChestConfiguration> entry : configByPos.entrySet()) {
            ChestConfiguration cfg = entry.getValue();
            if (cfg.getRole() != ChestConfiguration.Role.STORAGE) continue;
            // Iterate the TYPE-keyed cache; strict merging happens per-chest on the raw stacks level
            for (Map.Entry<ItemKey, Integer> ce : cfg.getCachedContents().entrySet()) {
                globalIndex.computeIfAbsent(ce.getKey(), k -> new ArrayList<>())
                        .add(new LocatedStack(entry.getKey(), ce.getKey(), ce.getValue()));
            }
        }

        List<ConsolidationTask> tasks = new ArrayList<>();

        for (Map.Entry<ItemKey, List<LocatedStack>> e : globalIndex.entrySet()) {
            if (e.getValue().size() <= 1) continue; // only in one chest — nothing to consolidate

            // Pick primary = highest-scoring chest for a representative 1-count stack
            ItemStack representative = new ItemStack(
                    BuiltInRegistries.ITEM.get(e.getKey().id));
            BlockPos primary = ItemSortingEngine.findBestChest(representative, configByPos, botPos);
            if (primary == null) continue;

            for (LocatedStack loc : e.getValue()) {
                if (loc.pos.equals(primary)) continue;
                // FIX BUG #11: only move if primary has room
                ChestConfiguration primaryCfg = configByPos.get(primary);
                if (primaryCfg == null || primaryCfg.getCachedFreeSlots() <= 0) continue;
                tasks.add(new ConsolidationTask(loc.pos, primary,
                        e.getKey().id.toString(), loc.count));
            }
        }

        return tasks;
    }

    /** Represents a single consolidation move: take {@code itemId} stacks from {@code src} → put in {@code dst}. */
    public record ConsolidationTask(BlockPos src, BlockPos dst, String itemId, int count) {}

    /** Internal helper for globalIndex entries. */
    private record LocatedStack(BlockPos pos, ItemKey key, int count) {}

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
