package com.minemods.bettertwink.sorting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

/**
 * Менеджер для работы с инвентарем сундуков
 */
public class ChestInventoryManager {
    private static ChestInventoryManager INSTANCE;

    private ChestInventoryManager() {
    }

    public static ChestInventoryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ChestInventoryManager();
        }
        return INSTANCE;
    }

    /**
     * Returns number of container slots (total minus 36 player inventory slots).
     */
    private int getContainerSize(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - 36);
    }

    /**
     * Performs an immediate shift-click (QUICK_MOVE) on the given slot to move an item.
     * The {@code delayMs} parameter is advisory — actual timing is controlled externally
     * via the tick handler's {@code waitTicks} mechanism.
     */
    public void moveItemWithDelay(AbstractContainerMenu sourceChest, AbstractContainerMenu targetChest,
                                  int sourceSlot, long delayMs) {
        quickMoveSlot(sourceChest, sourceSlot);
    }

    /**
     * Performs a shift-click (QUICK_MOVE) on {@code slot} in {@code menu}.
     * Safe to call even if the Minecraft game mode is unavailable.
     */
    public static void quickMoveSlot(AbstractContainerMenu menu, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        ItemStack item = menu.getSlot(slot).getItem();
        if (item.isEmpty()) return;
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }

    /**
     * Performs a left-click PICKUP on {@code slot} (picks up or puts down the cursor stack).
     */
    public static void pickupSlot(AbstractContainerMenu menu, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
    }

    /**
     * Performs PICKUP_ALL (double-click) on {@code slot} to consolidate stacks.
     */
    public static void pickupAllSlot(AbstractContainerMenu menu, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, slot, 0, ClickType.PICKUP_ALL, mc.player);
    }

    /**
     * Получает количество свободного места в сундуке
     */
    public int getEmptySlots(AbstractContainerMenu chest) {
        int emptySlots = 0;
        int size = getContainerSize(chest);
        for (int i = 0; i < size; i++) {
            if (chest.getSlot(i).getItem().isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    /**
     * Проверяет, может ли сундук вместить предмет
     */
    public boolean canAcceptItem(AbstractContainerMenu chest, ItemStack item) {
        int size = getContainerSize(chest);
        for (int i = 0; i < size; i++) {
            ItemStack existingItem = chest.getSlot(i).getItem();
            if (existingItem.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(existingItem, item) &&
                existingItem.getCount() < existingItem.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Вычисляет гуманизированную задержку на основе количества предметов
     */
    public long calculateHumanizedDelay(int itemCount, int baseDelayMs) {
        long randomDelay = (long)(Math.random() * 50);
        return baseDelayMs + randomDelay;
    }

    /**
     * Найти предмет по типу в инвентаре
     */
    public int findItemSlot(AbstractContainerMenu chest, ItemStack item) {
        int size = getContainerSize(chest);
        for (int i = 0; i < size; i++) {
            ItemStack slotItem = chest.getSlot(i).getItem();
            if (ItemStack.isSameItemSameTags(slotItem, item)) {
                return i;
            }
        }
        return -1;
    }
}

