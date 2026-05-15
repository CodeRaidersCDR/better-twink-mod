package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.minemods.bettertwink.crafting.CraftingManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Экран глобальных правил авто-крафта.
 * Правила задаются как "minecraft:iron_ingot -> minecraft:iron_block".
 * Для выбора предметов используется поисковый дропдаун {@link ItemSearchDropdown}.
 */
public class CraftingRulesScreen extends Screen {

    private static final int W            = 320;
    private static final int H            = 200;
    private static final int ROW_H_LIST   = 13;
    private static final int VISIBLE_ROWS = 7;

    private final Screen previousScreen;

    private ItemSearchDropdown fromDropdown;
    private ItemSearchDropdown toDropdown;
    private int lx, ty;

    private List<Map.Entry<String, CraftingManager.CraftingRule>> ruleList = new ArrayList<>();
    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    public CraftingRulesScreen(Screen previousScreen) {
        super(Component.literal("Crafting Rules"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        lx = (this.width - W) / 2;
        ty = (this.height - H) / 2;

        refreshRules();

        // From: item search
        fromDropdown = new ItemSearchDropdown(font, lx + 5, ty + 5, 140,
                "From item...", id -> {});
        addRenderableWidget(fromDropdown.getEditBox());

        // To: item search
        toDropdown = new ItemSearchDropdown(font, lx + 155, ty + 5, 120,
                "To item...", id -> {});
        addRenderableWidget(toDropdown.getEditBox());

        // Add rule
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addRule())
                .pos(lx + 280, ty + 5).size(35, 14).build());

        // Remove selected
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelected())
                .pos(lx + 5, ty + H - 26).size(60, 16).build());

        // Back
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .pos(lx + W - 65, ty + H - 26).size(60, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        g.fill(lx,     ty,     lx+W,   ty+H,   0xCC181818);
        g.fill(lx,     ty,     lx+W,   ty+1,   0xFF556677);
        g.fill(lx,     ty+H-1, lx+W,   ty+H,   0xFF556677);
        g.fill(lx,     ty,     lx+1,   ty+H,   0xFF556677);
        g.fill(lx+W-1, ty,     lx+W,   ty+H,   0xFF556677);

        g.drawCenteredString(font, "\u00a7eGlobal Crafting Rules", lx+W/2, ty-12, 0xFFFFFF);

        // Стрелка между полями ввода
        g.drawString(font, "\u2192", lx + 148, ty + 7, 0xAAAAAA, false);

        // Заголовки колонок списка
        int listTop = ty + 26;
        g.drawString(font, "From item",       lx+8,   listTop, 0xAAAAAA, false);
        g.drawString(font, "\u2192 To item",  lx+168, listTop, 0xAAAAAA, false);
        g.fill(lx+5, listTop+9, lx+W-5, listTop+10, 0xFF444444);

        int rowStartY = listTop + 12;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= ruleList.size()) break;
            CraftingManager.CraftingRule rule = ruleList.get(idx).getValue();
            int rowY = rowStartY + i * ROW_H_LIST;
            boolean sel = (idx == selectedIndex);
            boolean hov = mx >= lx+5 && mx <= lx+W-8 && my >= rowY-1 && my < rowY+ROW_H_LIST-1;
            if (sel)      g.fill(lx+5, rowY-1, lx+W-8, rowY+ROW_H_LIST-1, 0xFF334455);
            else if (hov) g.fill(lx+5, rowY-1, lx+W-8, rowY+ROW_H_LIST-1, 0xFF222233);
            g.drawString(font, shortId(rule.fromItemId), lx+8,   rowY, 0xFFFFFF, false);
            g.drawString(font, "\u00a77\u2192 \u00a7f"+shortId(rule.toItemId), lx+168, rowY, 0xFFFFFF, false);
        }

        // Полоса прокрутки
        if (ruleList.size() > VISIBLE_ROWS) {
            int totalH = VISIBLE_ROWS * ROW_H_LIST;
            int thumbH = Math.max(8, totalH * VISIBLE_ROWS / ruleList.size());
            int thumbY = rowStartY + (totalH - thumbH) * scrollOffset
                    / Math.max(1, ruleList.size() - VISIBLE_ROWS);
            g.fill(lx+W-6, rowStartY, lx+W-3, rowStartY+totalH, 0xFF333333);
            g.fill(lx+W-6, thumbY,    lx+W-3, thumbY+thumbH,    0xFF888888);
        }

        g.fill(lx+5, ty+H-32, lx+W-5, ty+H-31, 0xFF444444);

        super.render(g, mx, my, pt); // рисует EditBox-ы и кнопки

        // Дропдауны поверх всего — рисуем последними
        fromDropdown.renderDropdown(g, mx, my);
        toDropdown.renderDropdown(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Сначала проверяем клики по дропдаунам (выбор предмета)
        if (fromDropdown.mouseClicked(mx, my, btn)) return true;
        if (toDropdown.mouseClicked(mx, my, btn))   return true;
        // Закрыть дропдаун при клике снаружи
        if (fromDropdown.isOpen() && !fromDropdown.isInsideArea(mx, my)) fromDropdown.close();
        if (toDropdown.isOpen()   && !toDropdown.isInsideArea(mx, my))   toDropdown.close();
        // Клик по строке списка правил
        int rowStartY = ty + 26 + 12;
        if (mx >= lx+5 && mx <= lx+W-8) {
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int idx = i + scrollOffset;
                if (idx >= ruleList.size()) break;
                int rowY = rowStartY + i * ROW_H_LIST;
                if (my >= rowY-1 && my < rowY+ROW_H_LIST-1) {
                    selectedIndex = idx;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (fromDropdown.mouseScrolled(mx, my, delta)) return true;
        if (toDropdown.mouseScrolled(mx, my, delta))   return true;
        int max = Math.max(0, ruleList.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int)Math.signum(delta), max));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Клавиатурная навигация по дропдауну активного поля
        ItemSearchDropdown active = null;
        if (fromDropdown.getEditBox().isFocused()) active = fromDropdown;
        else if (toDropdown.getEditBox().isFocused()) active = toDropdown;

        if (active != null && active.isOpen()) {
            if (keyCode == 264) { active.navigateDown();    return true; } // Down
            if (keyCode == 265) { active.navigateUp();      return true; } // Up
            if (keyCode == 256) { active.close();           return true; } // Escape
            if (keyCode == 257 || keyCode == 335) { active.selectHighlighted(); return true; } // Enter
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addRule() {
        String from = fromDropdown.getValue();
        String to   = toDropdown.getValue();
        if (from.isEmpty() || to.isEmpty()) return;
        CraftingManager.getInstance().addCraftingRule(from, to, from+"_to_"+to);
        fromDropdown.setValue("");
        toDropdown.setValue("");
        refreshRules();
    }

    private void removeSelected() {
        if (selectedIndex >= 0 && selectedIndex < ruleList.size()) {
            CraftingManager.getInstance().removeCraftingRule(ruleList.get(selectedIndex).getKey());
            selectedIndex = -1;
            refreshRules();
        }
    }

    private void refreshRules() {
        ruleList = new ArrayList<>(CraftingManager.getInstance().getAllRules().entrySet());
        scrollOffset  = Math.min(scrollOffset,  Math.max(0, ruleList.size()-VISIBLE_ROWS));
        selectedIndex = Math.min(selectedIndex, ruleList.size()-1);
    }

    private String shortId(String id) {
        if (id != null && id.startsWith("minecraft:")) return id.substring(10);
        return id != null ? id : "?";
    }

    @Override
    public void onClose() { minecraft.setScreen(previousScreen); }

    @Override
    public boolean isPauseScreen() { return false; }
}
