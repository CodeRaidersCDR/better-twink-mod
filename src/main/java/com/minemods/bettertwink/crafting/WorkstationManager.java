package com.minemods.bettertwink.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SmokerBlock;

import java.util.*;

/**
 * Registry of workstation positions scanned by the bot.
 *
 * <p>The bot registers workstations when they are found during chest scanning.
 * Before crafting, {@link #findBest} returns the nearest workstation capable
 * of processing a given recipe type.
 *
 * <p>Workstation types:
 * <ul>
 *   <li>{@link Type#CRAFTING_TABLE} — shaped/shapeless crafting recipes</li>
 *   <li>{@link Type#FURNACE} — smelting</li>
 *   <li>{@link Type#BLAST_FURNACE} — fast smelting of metals/armour</li>
 *   <li>{@link Type#SMOKER} — fast food cooking</li>
 * </ul>
 */
public class WorkstationManager {

    private static WorkstationManager INSTANCE;

    private final Map<BlockPos, Type> registry = new LinkedHashMap<>();

    private WorkstationManager() {}

    public static WorkstationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new WorkstationManager();
        return INSTANCE;
    }

    // ── Registration ──────────────────────────────────────────────────────

    /**
     * Register a workstation at the given position.
     * Called by the scanner when it encounters a workstation block.
     */
    public void register(BlockPos pos, Type type) {
        registry.put(pos, type);
    }

    /**
     * Detect and register a workstation block at {@code pos} based on its block type.
     * Returns true if a workstation was registered.
     */
    public boolean autoDetect(BlockPos pos,
                               net.minecraft.world.level.block.state.BlockState state) {
        Type type = null;
        if (state.getBlock() instanceof CraftingTableBlock) type = Type.CRAFTING_TABLE;
        else if (state.getBlock() instanceof BlastFurnaceBlock) type = Type.BLAST_FURNACE;
        else if (state.getBlock() instanceof SmokerBlock) type = Type.SMOKER;
        else if (state.getBlock() instanceof FurnaceBlock) type = Type.FURNACE;

        if (type != null) {
            registry.put(pos, type);
            return true;
        }
        return false;
    }

    /** Remove a workstation (e.g. block was broken). */
    public void unregister(BlockPos pos) {
        registry.remove(pos);
    }

    /** Clear all registrations (call on world disconnect). */
    public void clear() {
        registry.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /**
     * Find the nearest workstation that can process the given recipe,
     * preferring proximity to {@code botPos}.
     *
     * @return position of best workstation, or {@code null} if none registered
     */
    public BlockPos findBest(Recipe<?> recipe, BlockPos botPos) {
        Type required = requiredType(recipe);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, Type> e : registry.entrySet()) {
            if (e.getValue() != required) continue;
            double d = e.getKey().distSqr(botPos);
            if (d < bestDist) { bestDist = d; best = e.getKey(); }
        }
        return best;
    }

    /** Returns all registered workstation positions of a given type. */
    public List<BlockPos> getAllOfType(Type type) {
        return registry.entrySet().stream()
                .filter(e -> e.getValue() == type)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Map<BlockPos, Type> getAll() {
        return Collections.unmodifiableMap(registry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Determine which workstation type is needed for the given recipe. */
    public static Type requiredType(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe) return Type.CRAFTING_TABLE;
        if (recipe instanceof AbstractCookingRecipe cooking) {
            // Identify by recipe type id
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(cooking.getType());
            if (typeId != null) {
                String path = typeId.getPath();
                if (path.equals("blasting"))  return Type.BLAST_FURNACE;
                if (path.equals("smoking"))   return Type.SMOKER;
            }
            return Type.FURNACE;
        }
        return Type.CRAFTING_TABLE; // default fallback
    }

    // ── Enum ──────────────────────────────────────────────────────────────

    public enum Type {
        CRAFTING_TABLE,
        FURNACE,
        BLAST_FURNACE,
        SMOKER
    }
}
