package com.minemods.bettertwink.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * FIX BUG #6: Executes crafting rules via Minecraft's handlePlaceRecipe packet system.
 * All crafting is performed through legitimate client-server packets — no inventory hacks.
 *
 * <p>Call {@link #applyCraftRules} AFTER collectItemsToSort, BEFORE routing.
 * Call {@link #executeCraft} when an open crafting container is available.
 */
public final class CraftingExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    private CraftingExecutor() {}

    // ── Phase 1: evaluate rules and decide craft counts ──────────────────────

    /**
     * Evaluates all enabled {@link CraftRule}s against the currently scanned inventories
     * and the player's inventory, logging what would be crafted.
     *
     * <p>Returns a map of rule → craftCount for all rules that should fire this cycle.
     * The caller is responsible for executing the crafts in order.
     */
    public static Map<CraftRule, Integer> evaluateRules(
            List<CraftRule> rules,
            Map<BlockPos, List<ItemStack>> scannedInventories) {

        Map<CraftRule, Integer> result = new LinkedHashMap<>();
        if (rules == null || rules.isEmpty()) return result;

        Set<ResourceLocation> usedThisCycle = new HashSet<>();

        for (CraftRule rule : rules) {
            if (!rule.enabled) continue;
            if (usedThisCycle.contains(rule.recipeId)) continue;

            int total     = countTrigger(rule.triggerItem, scannedInventories);
            int available = total - rule.keepMinimum;
            if (available <= 0) continue;

            Optional<CraftingRecipe> recipeOpt = RecipeResolver.resolve(rule.recipeId);
            if (recipeOpt.isEmpty()) {
                LOGGER.warn("[CraftingExecutor] CRAFT: recipe not found: {}", rule.recipeId);
                continue;
            }

            int inputCount = RecipeResolver.ingredientCount(recipeOpt.get());
            if (inputCount <= 0) continue;

            int craftCount = available / inputCount;
            if (craftCount * inputCount < rule.threshold) continue;

            LOGGER.info("[CraftingExecutor] CRAFT: {} -> {} x{} (rule applied)",
                    rule.triggerItem, rule.resultItem, craftCount);
            result.put(rule, craftCount);
            usedThisCycle.add(rule.recipeId);
        }

        return result;
    }

    // ── Phase 2: execute a single craft rule ─────────────────────────────────

    /**
     * Executes a craft via {@code handlePlaceRecipe} + {@code QUICK_MOVE} to collect result.
     * Requires that a crafting container (or inventory menu for 2×2 recipes) is currently open.
     *
     * @param rule       the craft rule to execute
     * @param craftCount number of times to repeat (fill grid + grab result)
     * @return true if the craft packets were sent successfully
     */
    public static boolean executeCraft(CraftRule rule, int craftCount) {
        // FIX BUG #6: use handlePlaceRecipe + QUICK_MOVE to materialise crafts via packets
        Optional<CraftingRecipe> recipeOpt = RecipeResolver.resolve(rule.recipeId);
        if (recipeOpt.isEmpty()) {
            LOGGER.warn("[CRAFT] Recipe not found: {}", rule.recipeId);
            return false;
        }
        CraftingRecipe recipe = recipeOpt.get();

        AbstractContainerMenu menu = MC.player.containerMenu;

        // Use inventory 2×2 grid if recipe fits and workstation is INVENTORY_2x2
        boolean use2x2 = rule.workstation == CraftRule.Workstation.INVENTORY_2x2
                && recipe.canCraftInDimensions(2, 2);

        if (use2x2) {
            menu = MC.player.inventoryMenu;
        } else if (!(menu instanceof CraftingMenu)) {
            // No crafting table open yet — caller must open it first via navigation
            LOGGER.warn("[CRAFT] No CraftingMenu open for recipe {}", rule.recipeId);
            return false;
        }

        LOGGER.info("[CRAFT] opening crafting table at {}", rule.workstationPos);

        for (int i = 0; i < craftCount; i++) {
            // FIX BUG #6: handlePlaceRecipe fills the grid in one packet
            MC.gameMode.handlePlaceRecipe(menu.containerId, recipe, /*shift=*/ false);
            // Output slot 0 → QUICK_MOVE to player inventory
            MC.gameMode.handleInventoryMouseClick(
                    menu.containerId, 0, 0, ClickType.QUICK_MOVE, MC.player);
        }

        LOGGER.info("[CRAFT] handlePlaceRecipe x{} for {}", craftCount, rule.recipeId);
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static int countTrigger(ResourceLocation triggerItem,
                                   Map<BlockPos, List<ItemStack>> inventories) {
        int count = 0;
        for (List<ItemStack> stacks : inventories.values())
            for (ItemStack s : stacks)
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(triggerItem))
                    count += s.getCount();
        if (MC.player != null)
            for (ItemStack s : MC.player.getInventory().items)
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(triggerItem))
                    count += s.getCount();
        return count;
    }
}
