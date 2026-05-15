package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.minemods.bettertwink.client.bot.SortingBotController;
import com.minemods.bettertwink.client.events.ClientEventHandler;
import com.minemods.bettertwink.config.BetterTwinkConfig;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;
import com.minemods.bettertwink.data.ServerConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Главный экран настроек Better Twink.
 * START/STOP sorting, chest list, select mode, transfer delay, crafting rules.
 */
public class BetterTwinkSettingsScreen extends Screen {

    private static final int W = 320;
    private static final int H = 220;
    private static final int ROW_H = 13;
    private static final int VISIBLE_ROWS = 6;

    private final Screen previousScreen;
    private ServerConfiguration currentConfig;

    private final List<ChestConfiguration> displayedChests = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private Button startStopButton;
    private Button selectModeButton;
    private EditBox searchBox;

    private int lx;
    private int ty;

    public BetterTwinkSettingsScreen(Screen previousScreen) {
        super(Component.literal("Better Twink"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        lx = (this.width - W) / 2;
        ty = (this.height - H) / 2;

        currentConfig = ConfigurationManager.getInstance().getCurrentServerConfig();
        refreshList();

        // Row 1: START/STOP button
        startStopButton = Button.builder(startStopLabel(), b -> toggleBot())
                .pos(lx + 5, ty + 5)
                .size(140, 18)
                .build();
        addRenderableWidget(startStopButton);

        // Row 2: Transfer delay [-] [+]  |  [Crafting Rules]
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeDelay(-50))
                .pos(lx + 90, ty + 30)
                .size(14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeDelay(50))
                .pos(lx + 130, ty + 30)
                .size(14, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Crafting Rules"), b ->
                minecraft.setScreen(new CraftingRulesScreen(this)))
                .pos(lx + 210, ty + 30)
                .size(105, 14)
                .build());

        // Row 3: Search box  |  [+ Select Chests]
        searchBox = new EditBox(font, lx + 5, ty + 50, 150, 14, Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(t -> refreshList());
        addRenderableWidget(searchBox);

        selectModeButton = Button.builder(selectModeLabel(), b -> toggleSelectMode())
                .pos(lx + 210, ty + 50)
                .size(105, 14)
                .build();
        addRenderableWidget(selectModeButton);

        // Chest action row
        addRenderableWidget(Button.builder(Component.literal("Edit"), b -> editSelected())
                .pos(lx + 5, ty + H - 48)
                .size(60, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelected())
                .pos(lx + 70, ty + H - 48)
                .size(60, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Quick-Drop"), b -> toggleQuickDrop())
                .pos(lx + 135, ty + H - 48)
                .size(80, 16)
                .build());

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(lx + W - 65, ty + H - 26)
                .size(60, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        // Panel background + border
        g.fill(lx, ty, lx + W, ty + H, 0xCC181818);
        g.fill(lx, ty, lx + W, ty + 1, 0xFF556677);
        g.fill(lx, ty + H - 1, lx + W, ty + H, 0xFF556677);
        g.fill(lx, ty, lx + 1, ty + H, 0xFF556677);
        g.fill(lx + W - 1, ty, lx + W, ty + H, 0xFF556677);

        // Title
        g.drawCenteredString(font, "\u00a7eBetter Twink", lx + W / 2, ty - 12, 0xFFFFFF);

        // Bot status (right of start/stop)
        SortingBotController bot = SortingBotController.getInstance();
        String stateStr = bot.isRunning()
                ? "\u00a7a" + bot.getCurrentState().toString()
                : "\u00a77IDLE";
        g.drawString(font, "Status: " + stateStr, lx + 152, ty + 9, 0xFFFFFF, false);

        // Transfer delay label + value (value sits between [-] and [+] buttons)
        g.drawString(font, "Transfer delay:", lx + 5, ty + 33, 0xBBBBBB, false);
        g.drawString(font, BetterTwinkConfig.ITEM_TRANSFER_DELAY.get() + "ms", lx + 107, ty + 33, 0xFFFF55, false);

        // Chest list header line
        int listTop = ty + 70;
        g.drawString(font, "Chests (" + displayedChests.size() + ")", lx + 5, listTop - 12, 0xAAAAAA, false);
        g.fill(lx + 5, listTop - 2, lx + W - 5, listTop - 1, 0xFF444444);

        // Chest rows
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= displayedChests.size()) break;
            ChestConfiguration chest = displayedChests.get(idx);
            int rowY = listTop + i * ROW_H;
            boolean sel = (idx == selectedIndex);
            boolean hov = mx >= lx + 5 && mx <= lx + W - 8 && my >= rowY - 1 && my < rowY + ROW_H - 1;

            if (sel)      g.fill(lx + 5, rowY - 1, lx + W - 8, rowY + ROW_H - 1, 0xFF334455);
            else if (hov) g.fill(lx + 5, rowY - 1, lx + W - 8, rowY + ROW_H - 1, 0xFF222233);

            g.drawString(font, chest.getChestName(), lx + 8, rowY, sel ? 0xFFFFAA : 0xFFFFFF, false);

            // Tags on right
            int tagX = lx + 185;
            if (chest.isQuickDrop()) {
                g.drawString(font, "\u00a76[QD]", tagX, rowY, 0xFFFFFF, false);
                tagX += 26;
            }
            if (!chest.getAllowedMods().isEmpty()) {
                g.drawString(font, "\u00a7b[M:" + chest.getAllowedMods().size() + "]", tagX, rowY, 0xFFFFFF, false);
                tagX += 30;
            }
            if (!chest.getAllowedItems().isEmpty()) {
                g.drawString(font, "\u00a7a[I:" + chest.getAllowedItems().size() + "]", tagX, rowY, 0xFFFFFF, false);
            }
        }

        // Scrollbar
        if (displayedChests.size() > VISIBLE_ROWS) {
            int totalH = VISIBLE_ROWS * ROW_H;
            int thumbH = Math.max(8, totalH * VISIBLE_ROWS / displayedChests.size());
            int thumbY = listTop + (totalH - thumbH) * scrollOffset
                    / Math.max(1, displayedChests.size() - VISIBLE_ROWS);
            g.fill(lx + W - 7, listTop, lx + W - 4, listTop + totalH, 0xFF333333);
            g.fill(lx + W - 7, thumbY, lx + W - 4, thumbY + thumbH, 0xFF888888);
        }

        // Separator before action row
        g.fill(lx + 5, ty + H - 54, lx + W - 5, ty + H - 53, 0xFF444444);

        // Refresh dynamic button labels every frame
        startStopButton.setMessage(startStopLabel());
        selectModeButton.setMessage(selectModeLabel());

        super.render(g, mx, my, pt);
    }

    private void toggleBot() {
        SortingBotController bot = SortingBotController.getInstance();
        if (bot.isRunning()) bot.stopBot(); else bot.startBot();
    }

    private void changeDelay(int delta) {
        int v = Math.max(50, Math.min(2000, BetterTwinkConfig.ITEM_TRANSFER_DELAY.get() + delta));
        BetterTwinkConfig.ITEM_TRANSFER_DELAY.set(v);
    }

    private void toggleSelectMode() {
        if (ClientEventHandler.isSelectingChests()) {
            ClientEventHandler.stopSelectingChests();
        } else {
            ClientEventHandler.startSelectingChests(); // closes screen so player can open chests
        }
    }

    private void editSelected() {
        if (selectedIndex >= 0 && selectedIndex < displayedChests.size())
            minecraft.setScreen(new ChestDetailScreen(this, displayedChests.get(selectedIndex)));
    }

    private void removeSelected() {
        if (selectedIndex >= 0 && selectedIndex < displayedChests.size()) {
            currentConfig.removeChest(displayedChests.get(selectedIndex).getPosition().toString());
            ConfigurationManager.getInstance().saveCurrentServer();
            selectedIndex = -1;
            refreshList();
        }
    }

    private void toggleQuickDrop() {
        if (selectedIndex >= 0 && selectedIndex < displayedChests.size()) {
            ChestConfiguration c = displayedChests.get(selectedIndex);
            c.setQuickDrop(!c.isQuickDrop());
            ConfigurationManager.getInstance().saveCurrentServer();
        }
    }

    private Component startStopLabel() {
        return SortingBotController.getInstance().isRunning()
                ? Component.literal("\u00a7c\u25a0 STOP SORTING")
                : Component.literal("\u00a7a\u25ba START SORTING");
    }

    private Component selectModeLabel() {
        return ClientEventHandler.isSelectingChests()
                ? Component.literal("\u00a7c\u25a0 Stop Select")
                : Component.literal("+ Select Chests");
    }

    private void refreshList() {
        displayedChests.clear();
        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        for (ChestConfiguration c : currentConfig.getChests().values()) {
            if (q.isEmpty() || c.getChestName().toLowerCase().contains(q))
                displayedChests.add(c);
        }
        selectedIndex = Math.min(selectedIndex, displayedChests.size() - 1);
        scrollOffset = Math.min(scrollOffset, Math.max(0, displayedChests.size() - VISIBLE_ROWS));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int listTop = ty + 70;
        if (mx >= lx + 5 && mx <= lx + W - 8) {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= displayedChests.size()) break;
                int rowY = listTop + i * ROW_H;
                if (my >= rowY - 1 && my < rowY + ROW_H - 1) {
                    selectedIndex = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, displayedChests.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), max));
        return true;
    }

    @Override
    public void onClose() {
        if (ClientEventHandler.isSelectingChests()) ClientEventHandler.stopSelectingChests();
        ConfigurationManager.getInstance().saveCurrentServer();
        minecraft.setScreen(previousScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
