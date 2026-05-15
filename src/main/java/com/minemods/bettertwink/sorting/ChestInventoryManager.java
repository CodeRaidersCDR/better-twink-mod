package com.minemods.bettertwink.sorting;

import net.minecraft.world.inventory.AbstractContainerMenu;
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
     * Перемещает предмет из одного слота в другой с гуманизированной задержкой
     */
    public void moveItemWithDelay(AbstractContainerMenu sourceChest, AbstractContainerMenu targetChest,
                                  int sourceSlot, long delayMs) {
        // Будет реализовано с использованием Minecraft packet system
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

