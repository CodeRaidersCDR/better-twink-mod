package com.minemods.bettertwink.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Система для открытия дверей при навигации бота
 */
public class DoorController {
    private static DoorController INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();

    private DoorController() {
    }

    public static DoorController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DoorController();
        }
        return INSTANCE;
    }

    /**
     * Проверяет, есть ли дверь в позиции
     */
    public boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock;
    }

    /**
     * Проверяет, открыта ли дверь
     */
    public boolean isDoorOpen(BlockState state) {
        if (!isDoor(state)) {
            return false;
        }
        return state.getValue(BlockStateProperties.OPEN);
    }

    /**
     * Открывает дверь (отправляет блок обновления)
     */
    public void openDoor(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        
        if (!isDoor(state)) {
            return;
        }

        if (isDoorOpen(state)) {
            return; // Уже открыта
        }

        // Опускаем блок обновления для открытия двери
        // В реальности это требует отправки пакета на сервер
        BlockState newState = state.setValue(BlockStateProperties.OPEN, true);
        level.setBlock(pos, newState, 2);
        
        LOGGER.debug("Opened door at {}", pos);
    }

    /**
     * Закрывает дверь
     */
    public void closeDoor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        
        if (!isDoor(state)) {
            return;
        }

        if (!isDoorOpen(state)) {
            return; // Уже закрыта
        }

        BlockState newState = state.setValue(BlockStateProperties.OPEN, false);
        level.setBlock(pos, newState, 2);
        
        LOGGER.debug("Closed door at {}", pos);
    }

    /**
     * Находит двери на пути между позициями
     */
    public BlockPos[] findDoorsInPath(Level level, BlockPos start, BlockPos end) {
        java.util.List<BlockPos> doors = new java.util.ArrayList<>();
        
        // Простая проверка - ищем двери в очень общей области
        int minX = Math.min(start.getX(), end.getX()) - 1;
        int maxX = Math.max(start.getX(), end.getX()) + 1;
        int minY = Math.min(start.getY(), end.getY());
        int maxY = Math.max(start.getY(), end.getY()) + 2;
        int minZ = Math.min(start.getZ(), end.getZ()) - 1;
        int maxZ = Math.max(start.getZ(), end.getZ()) + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    
                    if (isDoor(state)) {
                        doors.add(pos);
                    }
                }
            }
        }

        return doors.toArray(new BlockPos[0]);
    }

    /**
     * Проверяет, блокирует ли дверь путь
     */
    public boolean doesDoorBlockPath(BlockState state, BlockPos playerPos, BlockPos targetPos) {
        if (!isDoor(state)) {
            return false;
        }

        // Дверь блокирует путь только если она закрыта
        return !isDoorOpen(state);
    }

    /**
     * Автоматически открывает все двери на пути
     */
    public void openDoorsInPath(Level level, BlockPos start, BlockPos end, Player player) {
        BlockPos[] doorsInPath = findDoorsInPath(level, start, end);
        
        for (BlockPos doorPos : doorsInPath) {
            BlockState state = level.getBlockState(doorPos);
            if (!isDoorOpen(state)) {
                openDoor(level, doorPos, player);
            }
        }
        
        if (doorsInPath.length > 0) {
            LOGGER.info("Opened {} doors in path", doorsInPath.length);
        }
    }
}
