package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;

/**
 * Экран фильтра по модам для сундука.
 * Вводь mod id (напр. create, minecraft) → Add → появляется в списке.
 */
public class ModFilterScreen extends Screen {

    private static final int W = 260;
    private static final int H = 180;
    private static final int ROW_H = 12;
    private static final int VISIBLE_ROWS = 8;

    private final Screen previousScreen;
    private final ChestConfiguration chestConfig;

    private EditBox inputBox;
    private int lx, ty;
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    public ModFilterScreen(Screen previousScreen, ChestConfiguration chestConfig) {
        super(Component.literal("Mod Filter"));
        this.previousScreen = previousScreen;
        this.chestConfig = chestConfig;
    }

    @Override
    protected void init() {
        lx = (this.width - W) / 2;
        ty = (this.height - H) / 2;

        inputBox = new EditBox(font, lx + 5, ty + 5, 180, 14, Component.literal("mod id..."));
        inputBox.setMaxLength(50);
        inputBox.setHint(Component.literal("e.g. minecraft, create"));
        addRenderableWidget(inputBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addMod())
                .pos(lx + 190, ty + 5).size(30, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelected())
                .pos(lx + 5, ty + H - 24).size(60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .pos(lx + W - 65, ty + H - 24).size(60, 16).build());
    }

    private void addMod() {
        String id = inputBox.getValue().trim();
        if (!id.isEmpty() && !chestConfig.getAllowedMods().contains(id)) {
            chestConfig.getAllowedMods().add(id);
            ConfigurationManager.getInstance().saveCurrentServer();
            inputBox.setValue("");
        }
    }

    private void removeSelected() {
        if (selectedIndex >= 0 && selectedIndex < chestConfig.getAllowedMods().size()) {
            chestConfig.getAllowedMods().remove(selectedIndex);
            ConfigurationManager.getInstance().saveCurrentServer();
            selectedIndex = -1;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(lx, ty, lx + W, ty + H, 0xCC181818);
        g.fill(lx, ty, lx + W, ty + 1, 0xFF556677);
        g.fill(lx, ty + H - 1, lx + W, ty + H, 0xFF556677);
        g.fill(lx, ty, lx + 1, ty + H, 0xFF556677);
        g.fill(lx + W - 1, ty, lx + W, ty + H, 0xFF556677);
        g.drawCenteredString(font, "\u00a7bMod Filter", lx + W / 2, ty - 12, 0xFFFFFF);

        int listTop = ty + 26;
        g.drawString(font, "Allowed mods (" + chestConfig.getAllowedMods().size() + "):", lx + 5, listTop, 0xAAAAAA, false);
        int rowY0 = listTop + 11;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= chestConfig.getAllowedMods().size()) break;
            String mod = chestConfig.getAllowedMods().get(idx);
            int rowY = rowY0 + i * ROW_H;
            boolean sel = (idx == selectedIndex);
            boolean hov = mx >= lx + 5 && mx <= lx + W - 8 && my >= rowY - 1 && my < rowY + ROW_H - 1;
            if (sel)      g.fill(lx + 5, rowY - 1, lx + W - 8, rowY + ROW_H - 1, 0xFF334455);
            else if (hov) g.fill(lx + 5, rowY - 1, lx + W - 8, rowY + ROW_H - 1, 0xFF222233);
            g.drawString(font, mod, lx + 8, rowY, sel ? 0xFFFFAA : 0xFFFFFF, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int rowY0 = ty + 26 + 11;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= chestConfig.getAllowedMods().size()) break;
            int rowY = rowY0 + i * ROW_H;
            if (mx >= lx + 5 && mx <= lx + W - 8 && my >= rowY - 1 && my < rowY + ROW_H - 1) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, chestConfig.getAllowedMods().size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), max));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(previousScreen); }

    @Override
    public boolean isPauseScreen() { return false; }
}
