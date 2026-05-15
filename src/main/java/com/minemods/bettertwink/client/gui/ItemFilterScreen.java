package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;

/**
 * Экран фильтра конкретных предметов для сундука.
 * Поиск по названию/ID через {@link ItemSearchDropdown}.
 */
public class ItemFilterScreen extends Screen {

    private static final int W            = 260;
    private static final int H            = 180;
    private static final int ROW_H        = 12;
    private static final int VISIBLE_ROWS = 8;

    private final Screen             previousScreen;
    private final ChestConfiguration chestConfig;

    private ItemSearchDropdown searchDropdown;
    private int lx, ty;
    private int selectedIndex = -1;
    private int scrollOffset  = 0;

    public ItemFilterScreen(Screen previousScreen, ChestConfiguration chestConfig) {
        super(Component.literal("Item Filter"));
        this.previousScreen = previousScreen;
        this.chestConfig    = chestConfig;
    }

    @Override
    protected void init() {
        lx = (this.width - W) / 2;
        ty = (this.height - H) / 2;

        // Search field (ширина 215, затем кнопка Add шириной 30 с 5px отступом)
        searchDropdown = new ItemSearchDropdown(font, lx + 5, ty + 5, 215,
                "Search items...", id -> {});
        addRenderableWidget(searchDropdown.getEditBox());

        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addItem())
                .pos(lx + 224, ty + 5).size(30, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelected())
                .pos(lx + 5,     ty + H - 24).size(60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .pos(lx + W - 65, ty + H - 24).size(60, 16).build());
    }

    private void addItem() {
        String id = searchDropdown.getValue();
        if (!id.isEmpty() && !chestConfig.getAllowedItems().contains(id)) {
            chestConfig.getAllowedItems().add(id);
            ConfigurationManager.getInstance().saveCurrentServer();
            searchDropdown.setValue("");
        }
    }

    private void removeSelected() {
        if (selectedIndex >= 0 && selectedIndex < chestConfig.getAllowedItems().size()) {
            chestConfig.getAllowedItems().remove(selectedIndex);
            ConfigurationManager.getInstance().saveCurrentServer();
            selectedIndex = -1;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(lx,     ty,     lx+W,   ty+H,   0xCC181818);
        g.fill(lx,     ty,     lx+W,   ty+1,   0xFF556677);
        g.fill(lx,     ty+H-1, lx+W,   ty+H,   0xFF556677);
        g.fill(lx,     ty,     lx+1,   ty+H,   0xFF556677);
        g.fill(lx+W-1, ty,     lx+W,   ty+H,   0xFF556677);
        g.drawCenteredString(font, "\u00a7aItem Filter", lx+W/2, ty-12, 0xFFFFFF);

        int listTop = ty + 26;
        g.drawString(font, "Allowed items (" + chestConfig.getAllowedItems().size() + "):",
                lx+5, listTop, 0xAAAAAA, false);

        int rowY0 = listTop + 11;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= chestConfig.getAllowedItems().size()) break;
            String item = chestConfig.getAllowedItems().get(idx);
            int rowY = rowY0 + i * ROW_H;
            boolean sel = (idx == selectedIndex);
            boolean hov = mx >= lx+5 && mx <= lx+W-8 && my >= rowY-1 && my < rowY+ROW_H-1;
            if (sel)      g.fill(lx+5, rowY-1, lx+W-8, rowY+ROW_H-1, 0xFF334455);
            else if (hov) g.fill(lx+5, rowY-1, lx+W-8, rowY+ROW_H-1, 0xFF222233);
            g.drawString(font, item, lx+8, rowY, sel ? 0xFFFFAA : 0xFFFFFF, false);
        }

        super.render(g, mx, my, pt);

        // Дропдаун поверх всего
        searchDropdown.renderDropdown(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (searchDropdown.mouseClicked(mx, my, btn)) return true;
        if (searchDropdown.isOpen() && !searchDropdown.isInsideArea(mx, my)) searchDropdown.close();

        int rowY0 = ty + 26 + 11;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= chestConfig.getAllowedItems().size()) break;
            int rowY = rowY0 + i * ROW_H;
            if (mx >= lx+5 && mx <= lx+W-8 && my >= rowY-1 && my < rowY+ROW_H-1) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (searchDropdown.mouseScrolled(mx, my, delta)) return true;
        int max = Math.max(0, chestConfig.getAllowedItems().size()-VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int)Math.signum(delta), max));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchDropdown.getEditBox().isFocused() && searchDropdown.isOpen()) {
            if (keyCode == 264) { searchDropdown.navigateDown();    return true; } // Down
            if (keyCode == 265) { searchDropdown.navigateUp();      return true; } // Up
            if (keyCode == 256) { searchDropdown.close();           return true; } // Escape
            if (keyCode == 257 || keyCode == 335) { searchDropdown.selectHighlighted(); return true; } // Enter
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { minecraft.setScreen(previousScreen); }

    @Override
    public boolean isPauseScreen() { return false; }
}
