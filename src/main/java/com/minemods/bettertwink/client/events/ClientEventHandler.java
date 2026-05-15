package com.minemods.bettertwink.client.events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.minemods.bettertwink.BetterTwinkMod;
import com.minemods.bettertwink.client.gui.BetterTwinkSettingsScreen;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;
import com.minemods.bettertwink.data.ServerConfiguration;
import org.lwjgl.glfw.GLFW;

/**
 * Обработчик событий клиента для Better Twink
 */
@Mod.EventBusSubscriber(modid = BetterTwinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int SETTINGS_KEY = GLFW.GLFW_KEY_B; // Default hotkey - B for Better Twink
    private static boolean isSelectingChests = false;

    /**
     * Обработка нажатий клавиш
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (MC.screen != null) return; // Не обрабатываем если открран экран

        if (event.getKey() == SETTINGS_KEY && event.getAction() == GLFW.GLFW_PRESS) {
            openSettingsScreen();
        }
    }

    /**
     * Обработка открытия контейнера — добавляем его в конфиг при активном Select Mode
     */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Init.Post event) {
        if (!isSelectingChests) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;

        // Получаем позицию блока по прицелу игрока
        HitResult hitResult = MC.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        ServerConfiguration serverConfig = ConfigurationManager.getInstance().getCurrentServerConfig();
        String posKey = pos.toString();

        if (!serverConfig.getChests().containsKey(posKey)) {
            String blockName = (MC.level != null)
                    ? MC.level.getBlockState(pos).getBlock().getName().getString()
                    : "Container";

            ChestConfiguration chest = new ChestConfiguration(pos);
            chest.setChestName(blockName + " (" + pos.toShortString() + ")");
            serverConfig.addChest(chest);
            ConfigurationManager.getInstance().saveCurrentServer();

            if (MC.player != null) {
                MC.player.displayClientMessage(
                    Component.literal("\u00a7a[BetterTwink] \u00a7fAdded: \u00a7e" + chest.getChestName()
                        + "\u00a77  |  Press B to manage"), true);
            }
        } else {
            if (MC.player != null) {
                MC.player.displayClientMessage(
                    Component.literal("\u00a7e[BetterTwink] \u00a7fAlready tracked: " + pos.toShortString()), true);
            }
        }
    }

    /**
     * Открывает экран настроек
     */
    private static void openSettingsScreen() {
        MC.setScreen(new BetterTwinkSettingsScreen(MC.screen));
    }

    /**
     * Начинает выбор сундуков — закрывает текущий экран, чтобы можно было кликать по миру
     */
    public static void startSelectingChests() {
        isSelectingChests = true;
        // Закрываем экран настроек, чтобы игрок мог взаимодействовать с миром
        MC.tell(() -> {
            if (MC.screen instanceof BetterTwinkSettingsScreen) {
                MC.setScreen(null);
            }
        });
    }

    /**
     * Заканчивает выбор сундуков
     */
    public static void stopSelectingChests() {
        isSelectingChests = false;
    }

    public static boolean isSelectingChests() {
        return isSelectingChests;
    }
}
