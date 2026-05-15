package com.minemods.bettertwink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Утилиты для работы с блоками и позициями
 */
public class BlockUtils {
    
    /**
     * Проверяет, является ли блок сундуком
     */
    public static boolean isChest(Level level, BlockPos pos) {
        if (level == null || level.isOutsideBuildHeight(pos.getY()) || !level.isInWorldBounds(pos)) {
            return false;
        }
        
        var block = level.getBlockState(pos).getBlock();
        return block == Blocks.CHEST || 
               block == Blocks.TRAPPED_CHEST ||
               block == Blocks.BARREL;
    }

    /**
     * Получает соседние проходимые блоки
     */
    public static BlockPos[] getNearbyWalkableBlocks(Level level, BlockPos center, int radius) {
        java.util.List<BlockPos> walkable = new java.util.ArrayList<>();
        
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - 1; y <= center.getY() + 2; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    
                    if (isWalkable(level, pos)) {
                        walkable.add(pos);
                    }
                }
            }
        }
        
        return walkable.toArray(new BlockPos[0]);
    }

    /**
     * Проверяет, может ли на блоке стоять игрок
     */
    public static boolean isWalkable(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos.getY()) || !level.isInWorldBounds(pos)) {
            return false;
        }
        
        var blockState = level.getBlockState(pos);
        return blockState.isAir() || !blockState.isSolid();
    }

    /**
     * Получает расстояние между двумя позициями
     */
    public static double getDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Проверяет, видима ли позиция с другой позиции (примерная проверка)
     */
    public static boolean isLineOfSightClear(Level level, BlockPos from, BlockPos to) {
        // Простая проверка - нет ли сплошных блоков между точками
        // Более сложная реализация потребует трейсинга луча
        return true; // Placeholder
    }
}
