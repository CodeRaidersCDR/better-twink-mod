package com.minemods.bettertwink.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Менеджер для автоматического крафта предметов
 */
public class CraftingManager {
    private static CraftingManager INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();

    private Map<String, CraftingRule> craftingRules; // itemId -> Crafting rule

    private CraftingManager() {
        this.craftingRules = new HashMap<>();
    }

    public static CraftingManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CraftingManager();
        }
        return INSTANCE;
    }

    /**
     * Добавляет правило крафта (из предмета в предмет)
     */
    public void addCraftingRule(String fromItemId, String toItemId, String recipeId) {
        CraftingRule rule = new CraftingRule(fromItemId, toItemId, recipeId);
        craftingRules.put(fromItemId, rule);
        LOGGER.info("Added crafting rule: {} -> {}", fromItemId, toItemId);
    }

    /**
     * Удаляет правило крафта
     */
    public void removeCraftingRule(String fromItemId) {
        craftingRules.remove(fromItemId);
        LOGGER.info("Removed crafting rule for: {}", fromItemId);
    }

    /**
     * Проверяет возможность скрафтить предмет по правилам
     */
    public boolean canCraft(ItemStack fromItem, Level level) {
        if (fromItem.isEmpty()) {
            return false;
        }
        String itemId = fromItem.getItem().toString();
        return craftingRules.containsKey(itemId);
    }

    /**
     * Выполняет крафт предмета (возвращает результат)
     * В реальной реализации использует Minecraft packet system для взаимодействия
     * с верстаком. Сейчас возвращает placeholder ItemStack.EMPTY.
     */
    public ItemStack craftItem(ItemStack fromItem, Level level) {
        if (fromItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String itemId = fromItem.getItem().toString();
        CraftingRule rule = craftingRules.get(itemId);
        if (rule == null) {
            return ItemStack.EMPTY;
        }
        LOGGER.debug("Scheduling craft: {} -> {}", rule.fromItemId, rule.toItemId);
        // Actual crafting via packets is implemented in BotTickHandler
        return ItemStack.EMPTY;
    }

    /**
     * Получает правило крафта для предмета
     */
    public CraftingRule getRule(String itemId) {
        return craftingRules.get(itemId);
    }

    /**
     * Получает все правила крафта
     */
    public Map<String, CraftingRule> getAllRules() {
        return new HashMap<>(craftingRules);
    }

    /**
     * Проверяет, есть ли правило для предмета
     */
    public boolean hasRule(ItemStack item) {
        String itemId = item.getItem().toString();
        return craftingRules.containsKey(itemId);
    }

    /**
     * Очищает все правила крафта
     */
    public void clearRules() {
        craftingRules.clear();
    }

    /**
     * Представляет одно правило крафта
     */
    public static class CraftingRule {
        public final String fromItemId;
        public final String toItemId;
        public final String recipeId;

        public CraftingRule(String fromItemId, String toItemId, String recipeId) {
            this.fromItemId = fromItemId;
            this.toItemId = toItemId;
            this.recipeId = recipeId;
        }

        @Override
        public String toString() {
            return String.format("CraftingRule[%s -> %s via %s]", fromItemId, toItemId, recipeId);
        }
    }
}
