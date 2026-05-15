package com.minemods.bettertwink.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.minemods.bettertwink.BetterTwinkMod;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;
import com.minemods.bettertwink.client.bot.SortingBotController;

/**
 * FIX BUG #8: Marks QuickDrop chests as dirty and schedules re-sort when a player
 *              closes the chest (not the bot).
 *
 * FIX BUG #12: Locks chests for 600 ticks (30 s) when opened by the player so that
 *              the bot does not interfere with manual operations.
 */
@Mod.EventBusSubscriber(modid = BetterTwinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ContainerEventHandler {

    private static final Minecraft MC = Minecraft.getInstance();

    /** Set by ClientEventHandler (hit-result tracking) or requestOpenContainer in the bot. */
    private static BlockPos lastInteractedPos = null;

    /** True while the bot is the entity that opened the container. */
    private static boolean botOpenedCurrent = false;

    // ── Public API for other classes to push info ─────────────────────────────

    public static void notifyBotOpenedContainer(BlockPos pos) {
        lastInteractedPos = pos;
        botOpenedCurrent  = true;
    }

    public static void notifyPlayerInteracted(BlockPos pos) {
        lastInteractedPos = pos;
        botOpenedCurrent  = false;
    }

    // ── Screen open ───────────────────────────────────────────────────────────

    /**
     * FIX BUG #12: When the PLAYER opens a registered chest, lock it for 600 ticks
     * so the bot cannot schedule a move while the player is browsing.
     */
    @SubscribeEvent
    public static void onContainerOpen(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        if (MC.level == null || lastInteractedPos == null) return;
        if (botOpenedCurrent) return; // bot opened it — do not lock

        ChestConfiguration cfg = getConfig(lastInteractedPos);
        if (cfg != null) {
            // FIX BUG #12: lock chest for 600 ticks (30 s) to prevent bot interference
            long lockUntil = MC.level.getGameTime() + 600L;
            cfg.setLockedUntilTick(lockUntil);
        }
    }

    // ── Screen close ──────────────────────────────────────────────────────────

    /**
     * FIX BUG #8: When the PLAYER closes a QuickDrop chest, mark it dirty and
     * schedule a re-scan so the bot picks up the new contents promptly.
     */
    @SubscribeEvent
    public static void onContainerClose(ScreenEvent.Closing event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        if (lastInteractedPos == null) return;
        if (botOpenedCurrent) {
            botOpenedCurrent = false; // reset flag, nothing else to do
            return;
        }

        ChestConfiguration cfg = getConfig(lastInteractedPos);
        if (cfg != null && cfg.getRole() == ChestConfiguration.Role.QUICK_DROP) {
            cfg.setDirty(true);
            // FIX BUG #8: schedule a re-sort ~5 s (100 ticks) after the player closes QD
            SortingBotController.getInstance().scheduleRescan(100);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ChestConfiguration getConfig(BlockPos pos) {
        var chests = ConfigurationManager.getInstance().getCurrentServerConfig().getChests();
        return chests.get(pos.toString());
    }
}
