package com.minemods.bettertwink.client.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import com.minemods.bettertwink.BetterTwinkMod;
import com.minemods.bettertwink.client.bot.SortingBotController;
import com.minemods.bettertwink.client.stats.BotStats;
import com.minemods.bettertwink.client.storage.ConfigurationPersistence;
import com.minemods.bettertwink.crafting.RecipeGraph;
import com.minemods.bettertwink.data.ConfigurationManager;

/**
 * Главный обработчик тиков для обновления бота
 */
@Mod.EventBusSubscriber(modid = BetterTwinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BotTickHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static boolean isChestScreenOpen = false;
    /** true if we injected nav keys last tick — we must release them once nav stops */
    private static boolean navKeysInjected = false;
    /** Config key we last initialized for (serverAddress@playerName) — triggers reload when it changes */
    private static String lastConfigKey = null;

    /**
     * Phase.START — fires BEFORE Minecraft.tick() processes player input.
     * We inject key presses here so the normal physics engine actually moves
     * the player along the A* path (collision-aware, door-aware, jump-aware).
     */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (MC.player == null) return;

        SortingBotController bot = SortingBotController.getInstance();

        if (bot.isNavActive()) {
            // Face the next path node
            MC.player.setYRot(bot.getNavDesiredYaw());
            MC.player.setXRot(bot.getNavDesiredXRot());
            // Hold W (forward), sprint for faster travel
            MC.options.keyUp.setDown(true);
            MC.options.keySprint.setDown(true);
            // Jump only when the next node is above us AND we're on the ground
            boolean wantJump = bot.isNavJump() && MC.player.onGround();
            MC.options.keyJump.setDown(wantJump);
            navKeysInjected = true;
        } else if (navKeysInjected) {
            // Release all injected keys exactly once when navigation ends
            MC.options.keyUp.setDown(false);
            MC.options.keySprint.setDown(false);
            MC.options.keyJump.setDown(false);
            navKeysInjected = false;
        }
    }

    /**
     * Обновляет бота каждый клиентский тик
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.player == null || MC.level == null) {
            return;
        }

        // Load config from disk and init server once per connection / account switch
        String serverAddress = getServerAddress();
        String playerName    = MC.player.getGameProfile().getName();
        String configKey     = serverAddress + "@" + playerName;
        if (!configKey.equals(lastConfigKey)) {
            lastConfigKey = configKey;
            ConfigurationPersistence.loadConfigurations();
            ConfigurationManager.getInstance().setCurrentServer(serverAddress, getServerName(), playerName);
            // §8.2 Build recipe graph async on world/account login
            RecipeGraph.getInstance().invalidate();
            RecipeGraph.getInstance().buildAsync();
        }

        // Обновляем бота
        SortingBotController.getInstance().update(MC.player, MC.level);

        // Отслеживаем открытие/закрытие сундуков
        if (MC.screen instanceof AbstractContainerScreen) {
            isChestScreenOpen = true;
        } else if (isChestScreenOpen) {
            isChestScreenOpen = false;
        }
    }

    /**
     * Получает адрес текущего сервера
     */
    private static String getServerAddress() {
        if (MC.isLocalServer()) {
            return "localhost";
        } else if (MC.getCurrentServer() != null) {
            return MC.getCurrentServer().ip;
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

    /**
     * Проверяет, открыт ли экран сундука
     */
    public static boolean isChestOpen() {
        return isChestScreenOpen;
    }
}
