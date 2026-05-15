package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;

/**
 * Экран редактирования одного сундука: имя, quick-drop, фильтры по предметам и модам.
 * Правила крафта — глобальные, редактируются через CraftingRulesScreen из главного меню.
 */
public class ChestDetailScreen extends Screen {

    private static final int W = 300;
    private static final int H = 190;

    private final Screen previousScreen;
    private final ChestConfiguration chestConfig;

    private EditBox nameBox;
    private int lx, ty;

    public ChestDetailScreen(Screen previousScreen, ChestConfiguration chestConfig) {
        super(Component.literal("Chest Settings"));
        this.previousScreen = previousScreen;
        this.chestConfig = chestConfig;
    }

    @Override
    protected void init() {
        lx = (this.width - W) / 2;
        ty = (this.height - H) / 2;

        // Name row
        nameBox = new EditBox(font, lx + 5, ty + 5, 200, 14, Component.literal("Name"));
        nameBox.setValue(chestConfig.getChestName());
        nameBox.setMaxLength(50);
        addRenderableWidget(nameBox);

        // Quick Drop toggle
        String qdLabel = chestConfig.isQuickDrop() ? "\u00a76[QD] Quick-Drop: ON" : "Quick-Drop: OFF";
        addRenderableWidget(Button.builder(Component.literal(qdLabel), b -> {
            chestConfig.setQuickDrop(!chestConfig.isQuickDrop());
            // Re-init to refresh button label
            init();
        }).pos(lx + 210, ty + 5).size(85, 14).build());

        // Mod filter row
        addRenderableWidget(Button.builder(Component.literal("+ Add Mod"), b ->
                minecraft.setScreen(new ModFilterScreen(this, chestConfig)))
                .pos(lx + 5, ty + 26).size(80, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Mods"), b -> {
            chestConfig.getAllowedMods().clear();
            ConfigurationManager.getInstance().saveCurrentServer();
        }).pos(lx + 90, ty + 26).size(70, 14).build());

        // Item filter row
        addRenderableWidget(Button.builder(Component.literal("+ Add Item"), b ->
                minecraft.setScreen(new ItemFilterScreen(this, chestConfig)))
                .pos(lx + 5, ty + 46).size(80, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Items"), b -> {
            chestConfig.getAllowedItems().clear();
            ConfigurationManager.getInstance().saveCurrentServer();
        }).pos(lx + 90, ty + 46).size(70, 14).build());

        // Save
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .pos(lx + 5, ty + H - 24).size(80, 16).build());

        // Back
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .pos(lx + W - 85, ty + H - 24).size(80, 16).build());
    }

    private void saveAndClose() {
        if (nameBox != null && !nameBox.getValue().isBlank())
            chestConfig.setChestName(nameBox.getValue().trim());
        ConfigurationManager.getInstance().saveCurrentServer();
        minecraft.setScreen(previousScreen);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        g.fill(lx, ty, lx + W, ty + H, 0xCC181818);
        g.fill(lx, ty, lx + W, ty + 1, 0xFF556677);
        g.fill(lx, ty + H - 1, lx + W, ty + H, 0xFF556677);
        g.fill(lx, ty, lx + 1, ty + H, 0xFF556677);
        g.fill(lx + W - 1, ty, lx + W, ty + H, 0xFF556677);

        g.drawCenteredString(font, "\u00a7eChest Settings", lx + W / 2, ty - 12, 0xFFFFFF);

        // Position
        g.drawString(font, "\u00a77" + chestConfig.getPosition().toShortString(), lx + 5, ty + 67, 0xFFFFFF, false);

        int infoY = ty + 82;
        g.fill(lx + 5, infoY - 4, lx + W - 5, infoY - 3, 0xFF444444);

        // Mods section
        g.drawString(font, "\u00a7bAllowed Mods (" + chestConfig.getAllowedMods().size() + "):", lx + 5, infoY, 0xFFFFFF, false);
        int y = infoY + 11;
        for (int i = 0; i < Math.min(chestConfig.getAllowedMods().size(), 4); i++) {
            g.drawString(font, "  \u00a77" + chestConfig.getAllowedMods().get(i), lx + 5, y, 0xFFFFFF, false);
            y += 10;
        }
        if (chestConfig.getAllowedMods().size() > 4)
            g.drawString(font, "  \u00a77... +" + (chestConfig.getAllowedMods().size() - 4) + " more", lx + 5, y, 0xFFFFFF, false);

        // Items section
        int itemsY = infoY + 60;
        g.fill(lx + 5, itemsY - 4, lx + W - 5, itemsY - 3, 0xFF444444);
        g.drawString(font, "\u00a7aAllowed Items (" + chestConfig.getAllowedItems().size() + "):", lx + 5, itemsY, 0xFFFFFF, false);
        y = itemsY + 11;
        for (int i = 0; i < Math.min(chestConfig.getAllowedItems().size(), 2); i++) {
            g.drawString(font, "  \u00a77" + chestConfig.getAllowedItems().get(i), lx + 5, y, 0xFFFFFF, false);
            y += 10;
        }
        if (chestConfig.getAllowedItems().size() > 2)
            g.drawString(font, "  \u00a77... +" + (chestConfig.getAllowedItems().size() - 2) + " more", lx + 5, y, 0xFFFFFF, false);

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
