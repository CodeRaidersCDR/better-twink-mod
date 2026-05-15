package com.minemods.bettertwink.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Поисковое поле с выпадающим списком предметов из реестра.
 *
 * Использование:
 *   1. Создать экземпляр в init().
 *   2. Добавить EditBox в экран: addRenderableWidget(dropdown.getEditBox()).
 *   3. В render() вызвать dropdown.renderDropdown(g, mx, my) ПОСЛЕ super.render().
 *   4. В mouseClicked() вызвать dropdown.mouseClicked() ДО super.mouseClicked().
 *   5. В mouseScrolled() вызвать dropdown.mouseScrolled().
 *   6. В keyPressed() вызвать dropdown.keyPressed() если EditBox в фокусе.
 */
public class ItemSearchDropdown {

    public static final int ROW_H   = 16;  // высота одной строки (равна иконке 16×16)
    public static final int MAX_VIS = 8;   // максимум видимых строк
    public static final int BOX_H   = 14;  // высота EditBox

    private final Font             font;
    private final EditBox          searchBox;
    private final Consumer<String> onSelect;

    // публичные, чтобы экраны могли сформировать area-check без лишних геттеров
    public final int x, y, width;

    private List<ResourceLocation> matches          = new ArrayList<>();
    private int                    scrollOffset     = 0;
    private int                    highlightedIndex = -1; // клавиатурная навигация
    private boolean                open             = false;

    /**
     * @param font     шрифт экрана
     * @param x        левая граница (абсолютная позиция на экране)
     * @param y        верхняя граница
     * @param width    ширина поля и списка
     * @param hint     плейсхолдер в EditBox
     * @param onSelect вызывается при выборе предмета (передаёт registry ID)
     */
    public ItemSearchDropdown(Font font, int x, int y, int width,
                              String hint, Consumer<String> onSelect) {
        this.font     = font;
        this.x        = x;
        this.y        = y;
        this.width    = width;
        this.onSelect = onSelect;

        searchBox = new EditBox(font, x, y, width, BOX_H, Component.literal(hint));
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.literal(hint));
        searchBox.setResponder(this::onTextChanged);
    }

    // ─────────────────────── public API ──────────────────────────────────

    public EditBox getEditBox() { return searchBox; }
    public String  getValue()   { return searchBox.getValue().trim(); }
    public boolean isOpen()     { return open; }

    /** Устанавливает значение и закрывает дропдаун. */
    public void setValue(String v) {
        searchBox.setValue(v);
        open             = false;
        highlightedIndex = -1;
    }

    /** Закрыть дропдаун без изменения текста. */
    public void close() {
        open             = false;
        highlightedIndex = -1;
    }

    /**
     * Возвращает true, если точка (mx, my) находится внутри EditBox или открытого дропдауна.
     * Используется, чтобы решить, нужно ли закрыть дропдаун при клике снаружи.
     */
    public boolean isInsideArea(double mx, double my) {
        if (mx < x || mx >= x + width) return false;
        int bottom = open ? dropdownBottom() : y + BOX_H;
        return my >= y && my < bottom;
    }

    /** Нижняя граница открытого дропдауна. */
    public int dropdownBottom() {
        int rows = open ? Math.min(Math.max(matches.size() - scrollOffset, 0), MAX_VIS) : 0;
        return y + BOX_H + 2 + rows * ROW_H;
    }

    // ─────────────────── клавиатурная навигация ───────────────────────────

    /** Переместить выделение вниз. */
    public void navigateDown() {
        if (matches.isEmpty()) return;
        highlightedIndex = Math.min(highlightedIndex + 1, matches.size() - 1);
        if (highlightedIndex >= scrollOffset + MAX_VIS)
            scrollOffset = highlightedIndex - MAX_VIS + 1;
    }

    /** Переместить выделение вверх. */
    public void navigateUp() {
        if (matches.isEmpty()) return;
        if (highlightedIndex <= 0) { highlightedIndex = 0; return; }
        highlightedIndex--;
        if (highlightedIndex < scrollOffset) scrollOffset = highlightedIndex;
    }

    /** Выбрать выделенный (или первый) элемент. */
    public void selectHighlighted() {
        int idx = highlightedIndex >= 0 ? highlightedIndex : (matches.isEmpty() ? -1 : scrollOffset);
        if (idx >= 0 && idx < matches.size()) pickItem(idx);
    }

    // ───────────────────── фильтрация реестра ────────────────────────────

    private void onTextChanged(String text) {
        matches.clear();
        scrollOffset     = 0;
        highlightedIndex = -1;
        if (text.trim().isEmpty()) { open = false; return; }

        String lq = text.trim().toLowerCase();
        List<ResourceLocation> results = new ArrayList<>();

        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            // Пропускаем воздух
            if (rl.getPath().equals("air")) continue;

            Item   item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) continue;
            String name = item.getDefaultInstance().getHoverName().getString().toLowerCase();
            String id   = rl.toString().toLowerCase();

            if (name.contains(lq) || id.contains(lq)) {
                results.add(rl);
                if (results.size() >= 128) break; // cap
            }
        }

        // Сортировка: сначала те, чьё имя начинается с запроса, затем алфавит
        results.sort((a, b) -> {
            String an = BuiltInRegistries.ITEM.get(a).getDefaultInstance()
                    .getHoverName().getString().toLowerCase();
            String bn = BuiltInRegistries.ITEM.get(b).getDefaultInstance()
                    .getHoverName().getString().toLowerCase();
            boolean as = an.startsWith(lq), bs = bn.startsWith(lq);
            if (as != bs) return as ? -1 : 1;
            return an.compareTo(bn);
        });

        matches = results;
        open    = !matches.isEmpty();
    }

    // ──────────────────────── рендеринг ──────────────────────────────────

    /**
     * Отрисовывает выпадающий список поверх всего.
     * Вызывать ПОСЛЕ super.render() в экране.
     */
    public void renderDropdown(GuiGraphics g, int mx, int my) {
        if (!open || matches.isEmpty()) return;

        int visible = Math.min(matches.size() - scrollOffset, MAX_VIS);
        if (visible <= 0) return;

        int dy = y + BOX_H + 2;
        int dh = visible * ROW_H;

        // Тень
        g.fill(x + 2, dy + 2, x + width + 2, dy + dh + 2, 0x44000000);
        // Фон
        g.fill(x, dy, x + width, dy + dh, 0xFF141414);
        // Рамка
        g.fill(x,           dy,       x + width,   dy + 1,      0xFF4A6688);
        g.fill(x,           dy + dh,  x + width,   dy + dh + 1, 0xFF4A6688);
        g.fill(x,           dy,       x + 1,       dy + dh + 1, 0xFF4A6688);
        g.fill(x + width-1, dy,       x + width,   dy + dh + 1, 0xFF4A6688);

        for (int i = 0; i < visible; i++) {
            int idx  = i + scrollOffset;
            if (idx >= matches.size()) break;

            ResourceLocation rl   = matches.get(idx);
            Item             item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) continue;

            String name  = item.getDefaultInstance().getHoverName().getString();
            int    rowY  = dy + i * ROW_H;
            boolean kbd  = (idx == highlightedIndex);
            boolean hov  = mx >= x+1 && mx < x+width-1 && my >= rowY && my < rowY + ROW_H;

            if (kbd)      g.fill(x+1, rowY, x+width-1, rowY+ROW_H, 0xFF3A5566);
            else if (hov) g.fill(x+1, rowY, x+width-1, rowY+ROW_H, 0xFF2A3F55);

            // Иконка предмета (16×16)
            g.renderItem(new ItemStack(item), x + 2, rowY);

            // Название (справа от иконки)
            int textColor = (kbd || hov) ? 0xFFFFAA : 0xEEEEEE;
            g.drawString(font, name, x + 21, rowY + 4, textColor, false);
        }

        // Полоса прокрутки
        if (matches.size() > MAX_VIS) {
            int sbX    = x + width - 4;
            int totalH = MAX_VIS * ROW_H;
            int thmH   = Math.max(8, totalH * MAX_VIS / matches.size());
            int max    = Math.max(1, matches.size() - MAX_VIS);
            int thmY   = dy + 1 + (totalH - thmH - 2) * scrollOffset / max;
            g.fill(sbX, dy+1,  sbX+3, dy+totalH-1, 0xFF2A2A2A);
            g.fill(sbX, thmY,  sbX+3, thmY+thmH,   0xFF667788);
        }
    }

    // ───────────────────── события мыши ──────────────────────────────────

    /**
     * @return true если клик поглощён дропдауном (выбор элемента).
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        int dy      = y + BOX_H + 2;
        int visible = Math.min(matches.size() - scrollOffset, MAX_VIS);
        int dh      = visible * ROW_H;
        if (mx >= x && mx < x + width && my >= dy && my < dy + dh) {
            int i = (int)((my - dy) / ROW_H);
            pickItem(i + scrollOffset);
            return true;
        }
        return false;
    }

    /**
     * @return true если колесо прокрутки поглощено дропдауном.
     */
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!open || matches.isEmpty()) return false;
        int dy = y + BOX_H + 2;
        int dh = Math.min(matches.size(), MAX_VIS) * ROW_H;
        if (mx >= x && mx < x + width && my >= dy && my < dy + dh) {
            int maxOff = Math.max(0, matches.size() - MAX_VIS);
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)Math.signum(delta), maxOff));
            return true;
        }
        return false;
    }

    // ───────────────────────── private ───────────────────────────────────

    private void pickItem(int idx) {
        if (idx < 0 || idx >= matches.size()) return;
        String id = matches.get(idx).toString();
        searchBox.setValue(id);
        open             = false;
        highlightedIndex = -1;
        onSelect.accept(id);
    }
}
