package com.minemods.bettertwink.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Async recipe dependency graph built from the server's RecipeManager on world login.
 *
 * <p>The graph has two indexes:
 * <ul>
 *   <li><b>producers</b>: outputItem → list of {@link RecipeNode}s that produce it.</li>
 *   <li><b>consumers</b>: inputItem → list of {@link RecipeNode}s that consume it.</li>
 * </ul>
 *
 * <p>Dijkstra-based {@link #planCraft} finds the shortest crafting path
 * (minimum ingredient cost) from a set of available items to a target output.
 *
 * <p>Uses {@code recipe.getId()} which is valid in Forge 1.20.1
 * (the method was removed with RecipeHolder in 1.20.2).
 */
public class RecipeGraph {

    private static RecipeGraph INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    /** output item id → recipes that produce it */
    private volatile Map<ResourceLocation, List<RecipeNode>> producers = Collections.emptyMap();
    /** input item id → recipes that consume it */
    private volatile Map<ResourceLocation, List<RecipeNode>> consumers = Collections.emptyMap();

    private volatile boolean built    = false;
    private volatile boolean building = false;

    private RecipeGraph() {}

    public static RecipeGraph getInstance() {
        if (INSTANCE == null) INSTANCE = new RecipeGraph();
        return INSTANCE;
    }

    public boolean isBuilt() { return built; }

    /** Invalidate on world disconnect so graph is rebuilt on next login. */
    public void invalidate() {
        built    = false;
        building = false;
        producers = Collections.emptyMap();
        consumers = Collections.emptyMap();
    }

    /**
     * Starts building the graph asynchronously using the common ForkJoinPool.
     * Safe to call multiple times — ignored if already built or building.
     */
    public void buildAsync() {
        if (built || building) return;
        building = true;
        CompletableFuture.runAsync(this::build, ForkJoinPool.commonPool())
                .exceptionally(ex -> { LOGGER.error("[BetterTwink] RecipeGraph build failed", ex);
                    building = false; return null; });
    }

    // ── Build ─────────────────────────────────────────────────────────────

    private synchronized void build() {
        if (MC.level == null) { building = false; return; }
        RecipeManager rm = MC.level.getRecipeManager();
        RegistryAccess reg = MC.level.registryAccess();

        Map<ResourceLocation, List<RecipeNode>> prod = new HashMap<>();
        Map<ResourceLocation, List<RecipeNode>> cons = new HashMap<>();

        for (Recipe<?> recipe : rm.getRecipes()) {
            // recipe.getId() exists in 1.20.1 (removed in 1.20.2 with RecipeHolder)
            ResourceLocation recipeId = recipe.getId();
            ItemStack result = recipe.getResultItem(reg);
            if (result.isEmpty()) continue;

            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            List<ResourceLocation> inputIds = new ArrayList<>();

            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                // getItems() includes all possible substitutes; use first as canonical
                ItemStack[] choices = ing.getItems();
                if (choices.length == 0) continue;
                inputIds.add(BuiltInRegistries.ITEM.getKey(choices[0].getItem()));
            }

            if (inputIds.isEmpty()) continue;

            RecipeNode node = new RecipeNode(recipeId, outputId, result.getCount(), inputIds, 1);
            prod.computeIfAbsent(outputId, k -> new ArrayList<>()).add(node);
            for (ResourceLocation inp : inputIds) {
                cons.computeIfAbsent(inp, k -> new ArrayList<>()).add(node);
            }
        }

        producers = prod;
        consumers = cons;
        built    = true;
        building = false;
        LOGGER.info("[BetterTwink] RecipeGraph built: {} outputs, {} inputs tracked",
                prod.size(), cons.size());
    }

    // ── Dijkstra craft planner ─────────────────────────────────────────────

    /**
     * Plans a crafting sequence to produce {@code needed} units of {@code target}
     * starting from {@code available} item set.
     *
     * @param available item ids the bot currently has
     * @param target    item id to produce
     * @param needed    number of output items needed (rounded up to recipe multiples)
     * @return ordered list of {@link RecipeStep}s, or empty if not craftable
     */
    public List<RecipeStep> planCraft(Set<ResourceLocation> available,
                                      ResourceLocation target,
                                      int needed) {
        if (!built) return Collections.emptyList();
        List<RecipeNode> targetProducers = producers.getOrDefault(target, List.of());
        if (targetProducers.isEmpty()) return Collections.emptyList();

        // Dijkstra over item-space: cost = total ingredient items consumed
        // dist[itemId] = minimum "ingredient-cost" to obtain said item
        Map<ResourceLocation, Long> dist = new HashMap<>();
        Map<ResourceLocation, RecipeNode> via = new HashMap<>();
        PriorityQueue<Map.Entry<ResourceLocation, Long>> pq =
                new PriorityQueue<>(Map.Entry.comparingByValue());

        for (ResourceLocation avail : available) {
            dist.put(avail, 0L);
            pq.offer(Map.entry(avail, 0L));
        }

        while (!pq.isEmpty()) {
            Map.Entry<ResourceLocation, Long> top = pq.poll();
            ResourceLocation cur = top.getKey();
            long curDist = top.getValue();
            if (curDist > dist.getOrDefault(cur, Long.MAX_VALUE)) continue;

            // Try every recipe that has 'cur' as an ingredient
            for (RecipeNode recipe : consumers.getOrDefault(cur, List.of())) {
                // Cost = number of distinct ingredient types (simplified)
                long cost = recipe.inputIds().size();
                long newDist = curDist + cost;
                ResourceLocation output = recipe.outputItem();
                if (newDist < dist.getOrDefault(output, Long.MAX_VALUE)) {
                    dist.put(output, newDist);
                    via.put(output, recipe);
                    pq.offer(Map.entry(output, newDist));
                }
            }
        }

        // Reconstruct path from available → target
        if (!dist.containsKey(target)) return Collections.emptyList();

        List<RecipeStep> steps = new ArrayList<>();
        Set<ResourceLocation> visited = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        queue.add(target);

        while (!queue.isEmpty()) {
            ResourceLocation item = queue.poll();
            if (available.contains(item) || visited.contains(item)) continue;
            visited.add(item);

            RecipeNode recipe = via.get(item);
            if (recipe == null) continue;

            // How many times do we need to run the recipe?
            int timesNeeded = (int) Math.ceil((double) needed / recipe.outputCount());
            steps.add(new RecipeStep(recipe.recipeId(), timesNeeded, item));

            // Enqueue ingredients that we don't already have
            for (ResourceLocation inp : recipe.inputIds()) {
                if (!available.contains(inp)) queue.add(inp);
            }
        }

        Collections.reverse(steps); // dependencies first
        return steps;
    }

    // ── Convenience queries ───────────────────────────────────────────────

    /** Returns all recipes that produce the given item, or empty list. */
    public List<RecipeNode> getProducers(ResourceLocation itemId) {
        return producers.getOrDefault(itemId, Collections.emptyList());
    }

    /** Returns true if this item can be crafted given the available input set. */
    public boolean isCraftable(Set<ResourceLocation> available, ResourceLocation target) {
        return !planCraft(available, target, 1).isEmpty();
    }

    // ── Records ───────────────────────────────────────────────────────────

    /**
     * A node in the recipe graph: one recipe entry with its output and inputs.
     */
    public record RecipeNode(
            ResourceLocation recipeId,
            ResourceLocation outputItem,
            int              outputCount,
            List<ResourceLocation> inputIds,
            int              inputCount) {}

    /**
     * A planned crafting step: run {@code recipeId} exactly {@code times} times
     * to produce {@code targetItem}.
     */
    public record RecipeStep(
            ResourceLocation recipeId,
            int              times,
            ResourceLocation targetItem) {}
}
