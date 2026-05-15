package com.minemods.bettertwink.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Manages crafting rules and provides helpers to execute crafts via the Minecraft packet system.
 *
 * <p>Rules ({@link CraftRule}) are evaluated here; actual multi-tick execution is driven
 * by the bot tick handler using the helper methods below.
 */
public class CraftingManager {

    private static CraftingManager INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Legacy string-keyed rules kept for backward compatibility with GUI code. */
    private final Map<String, CraftingRule> craftingRules = new HashMap<>();

    private CraftingManager() {}

    public static CraftingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new CraftingManager();
        return INSTANCE;
    }

    // в”Ђв”Ђ Legacy API в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    public void addCraftingRule(String fromItemId, String toItemId, String recipeId) {
        craftingRules.put(fromItemId, new CraftingRule(fromItemId, toItemId, recipeId));
        LOGGER.info("Added crafting rule: {} -> {}", fromItemId, toItemId);
    }

    public void removeCraftingRule(String fromItemId) {
        craftingRules.remove(fromItemId);
        LOGGER.info("Removed crafting rule for: {}", fromItemId);
    }

    public boolean canCraft(ItemStack fromItem) {
        if (fromItem.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(fromItem.getItem()).toString();
        return craftingRules.containsKey(itemId);
    }

    public CraftingRule getRule(String itemId) { return craftingRules.get(itemId); }
    public Map<String, CraftingRule> getAllRules() { return new HashMap<>(craftingRules); }
    public boolean hasRule(ItemStack item) {
        return craftingRules.containsKey(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
    }
    public void clearRules() { craftingRules.clear(); }

    // в”Ђв”Ђ New API using CraftRule в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    /**
     * Counts how many times {@code rule} should fire given the current inventories.
     *
     * @param rule        the craft rule to evaluate
     * @param inventories all chest-contents or player inventory to count trigger items
     * @return number of times to craft (0 = skip)
     */
    public int countCraftTimes(CraftRule rule, Map<BlockPos, List<ItemStack>> inventories) {
        if (!rule.enabled) return 0;
        int total     = countItems(rule.triggerItem, inventories);
        int available = total - rule.keepMinimum;
        if (available < rule.threshold) return 0;
        Optional<CraftingRecipe> recipe = getRecipe(rule.recipeId);
        if (recipe.isEmpty()) {
            LOGGER.warn("[CraftingManager] Recipe not found: {}", rule.recipeId);
            return 0;
        }
        int inputCount = (int) recipe.get().getIngredients().stream()
                .filter(ing -> !ing.isEmpty()).count();
        if (inputCount <= 0) return 0;
        return available / inputCount;
    }

    /**
     * Looks up a {@link CraftingRecipe} by resource location from the client recipe manager.
     */
    @SuppressWarnings("unchecked")
    public Optional<CraftingRecipe> getRecipe(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();
        try {
            Optional<? extends Recipe<?>> opt = mc.level.getRecipeManager().byKey(id);
            return opt.filter(r -> r instanceof CraftingRecipe)
                      .map(r -> (CraftingRecipe) r);
        } catch (Exception e) {
            LOGGER.warn("[CraftingManager] Failed to look up recipe {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Sends a {@code handlePlaceRecipe} packet to fill the crafting grid.
     * Requires that a crafting container is currently open.
     *
     * @param menu   the open crafting/inventory container
     * @param recipe the crafting recipe to place
     * @return true if the packet was sent
     */
    public boolean placeRecipe(AbstractContainerMenu menu, CraftingRecipe recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        try {
            mc.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[CraftingManager] handlePlaceRecipe failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Takes the crafting result from slot 0 via QUICK_MOVE.
     * Call this after {@link #placeRecipe} and a brief tick wait.
     */
    public void collectResult(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 0, 0, ClickType.QUICK_MOVE, mc.player);
    }

    // в”Ђв”Ђ Helpers в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    private static int countItems(ResourceLocation id, Map<BlockPos, List<ItemStack>> inventories) {
        int count = 0;
        for (List<ItemStack> stacks : inventories.values())
            for (ItemStack s : stacks)
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(id))
                    count += s.getCount();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack s : mc.player.getInventory().items)
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(id))
                    count += s.getCount();
        }
        return count;
    }

    // в”Ђв”Ђ Legacy inner class в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    public static class CraftingRule {
        public final String fromItemId;
        public final String toItemId;
        public final String recipeId;

        public CraftingRule(String fromItemId, String toItemId, String recipeId) {
            this.fromItemId = fromItemId;
            this.toItemId   = toItemId;
            this.recipeId   = recipeId;
        }

        @Override
        public String toString() {
            return String.format("CraftingRule[%s -> %s via %s]", fromItemId, toItemId, recipeId);
        }
    }
}

