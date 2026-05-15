package com.minemods.bettertwink.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Утилиты для работы с предметами
 */
public class ItemUtils {

    /**
     * Получает уникальный идентификатор предмета в формате "minecraft:iron_ingot".
     */
    public static String getItemId(ItemStack item) {
        if (item.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    /**
     * Получает mod-id предмета ("minecraft", "apotheosis", и т.д.)
     */
    public static String getModId(ItemStack item) {
        if (item.isEmpty()) return "unknown";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item.getItem());
        return key.getNamespace();
    }

    /**
     * Проверяет, можно ли сложить два предмета
     */
    public static boolean canStack(ItemStack item1, ItemStack item2) {
        if (item1.isEmpty() || item2.isEmpty()) {
            return false;
        }
        
        return ItemStack.isSameItemSameTags(item1, item2) &&
               item1.getCount() < item1.getMaxStackSize();
    }

    /**
     * Получает количество свободного пространства в стеке
     */
    public static int getStackSpace(ItemStack item) {
        if (item.isEmpty()) {
            return 64; // Default stack size
        }
        return item.getMaxStackSize() - item.getCount();
    }

    /**
     * Получает кол-во предметов, которое можно добавить в стек
     */
    public static int getAddableAmount(ItemStack source, ItemStack target) {
        if (!canStack(source, target)) {
            return 0;
        }
        
        return Math.min(source.getCount(), getStackSpace(target));
    }

    /**
     * Скопирует предмет с определенным количеством
     */
    public static ItemStack copyWithCount(ItemStack item, int count) {
        ItemStack copy = item.copy();
        copy.setCount(count);
        return copy;
    }

    /**
     * Получает полное описание предмета для отладки
     */
    public static String getDetailedDescription(ItemStack item) {
        if (item.isEmpty()) {
            return "Empty";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(getItemId(item))
          .append(" x").append(item.getCount())
          .append(" [").append(getModId(item)).append("]");
        
        if (item.hasTag()) {
            sb.append(" {NBT}");
        }
        
        return sb.toString();
    }
}
