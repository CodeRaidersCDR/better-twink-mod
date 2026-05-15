package com.minemods.bettertwink.client.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import com.minemods.bettertwink.BetterTwinkMod;
import com.minemods.bettertwink.client.gui.BetterTwinkSettingsScreen;
import com.minemods.bettertwink.data.ConfigurationManager;

/**
 * Обработчик событий сортировки
 */
@Mod.EventBusSubscriber(modid = BetterTwinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SortingEventHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static long lastSortCheck = 0;
    private static final long SORT_CHECK_INTERVAL = 1000; // Проверяем каждую секунду

    /**
     * Проверяет нужна ли сортировка при открытии сундука
     */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Init.Post event) {
        if (MC.player == null) return;

        if (event.getScreen() instanceof BetterTwinkSettingsScreen) {
            // Экран настроек открыт - инициализируем конфигурацию для текущего сервера
            String serverAddress = getServerAddress();
            String serverName = getServerName();
            ConfigurationManager.getInstance().setCurrentServer(serverAddress, serverName);
        }
    }

    /**
     * Получает адрес текущего сервера
     */
    private static String getServerAddress() {
        if (MC.player != null && MC.player.level() != null) {
            if (MC.isLocalServer()) {
                return "localhost";
            } else if (MC.getCurrentServer() != null) {
                return MC.getCurrentServer().ip;
            }
        }
        return "unknown";
    }

    /**
     * Получает имя сервера
     */
    private static String getServerName() {
        if (MC.getCurrentServer() != null) {
            return MC.getCurrentServer().name;
        }
        return "Local Server";
    }
}
