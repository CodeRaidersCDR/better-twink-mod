package com.minemods.bettertwink.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import com.minemods.bettertwink.sorting.ItemKey;
import com.minemods.bettertwink.sorting.ItemSortingEngine;
import com.minemods.bettertwink.sorting.ItemSortingEngine.ItemCategory;
import com.minemods.bettertwink.crafting.CraftRule;

import java.util.*;

/**
 * Конфигурация одного сундука: роль, фильтры, крафт-правила, кэш содержимого.
 */
public class ChestConfiguration {

    // ── Роль сундука ─────────────────────────────────────────────
    public enum Role { STORAGE, QUICK_DROP, OVERFLOW, CRAFT_INPUT, CRAFT_OUTPUT, TRASH }

    // ── Фильтры ───────────────────────────────────────────────────
    public enum FilterMode { ALLOW, DENY }
    public enum FilterType { EXACT_ID, MOD_ID, CATEGORY }

    public static final class ItemFilter {
        public final FilterMode mode;
        public final FilterType type;
        public final String value;
        public ItemFilter(FilterMode mode, FilterType type, String value) {
            this.mode = mode; this.type = type; this.value = value;
        }
    }

    // ── Поля ──────────────────────────────────────────────────────
    private BlockPos     position;
    private String       chestName;
    private Role         role            = Role.STORAGE;
    private int          priority        = 50;
    private List<ItemFilter>  filters    = new ArrayList<>();
    private ItemCategory pinnedCategory  = null;
    private List<CraftRule>   craftRulesNew = new ArrayList<>();
    private Map<ItemKey, Integer> cachedContents = new LinkedHashMap<>();
    private int          cachedFreeSlots = 54;
    private boolean      dirty           = true;
    private long         lastScanTick    = 0;
    private long         lockedUntilTick = 0;

    // Legacy (backward compat for GUI / serialization)
    private List<String>  allowedMods  = new ArrayList<>();
    private List<String>  allowedItems = new ArrayList<>();

    public ChestConfiguration(BlockPos position) {
        this.position  = position;
        this.chestName = "Chest " + position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    // ── Геттеры ───────────────────────────────────────────────────
    public BlockPos          getPosition()        { return position; }
    public String            getChestName()       { return chestName; }
    public Role              getRole()            { return role; }
    public int               getPriority()        { return priority; }
    public List<ItemFilter>  getFilters()         { return filters; }
    public ItemCategory      getPinnedCategory()  { return pinnedCategory; }
    public List<CraftRule>   getNewCraftRules()   { return craftRulesNew; }
    public Map<ItemKey,Integer> getCachedContents(){ return cachedContents; }
    public int               getCachedFreeSlots() { return cachedFreeSlots; }
    public boolean           isDirty()            { return dirty; }
    public long              getLastScanTick()    { return lastScanTick; }
    public long              getLockedUntilTick() { return lockedUntilTick; }
    public List<String>      getAllowedMods()     { return allowedMods; }
    public List<String>      getAllowedItems()    { return allowedItems; }
    public boolean           isQuickDrop()       { return role == Role.QUICK_DROP; }
    public List<Integer>     getCraftRules()     { return List.of(); } // legacy stub

    // ── Сеттеры ───────────────────────────────────────────────────
    public void setChestName(String n)           { chestName = n; }
    public void setRole(Role r)                  { role = r; }
    public void setPriority(int p)               { priority = Math.max(0, Math.min(100, p)); }
    public void setPinnedCategory(ItemCategory c){ pinnedCategory = c; }
    public void setDirty(boolean d)              { dirty = d; }
    public void setLastScanTick(long t)          { lastScanTick = t; }
    public void setCachedFreeSlots(int s)        { cachedFreeSlots = s; }
    public void setLockedUntilTick(long t)       { lockedUntilTick = t; }
    public void setQuickDrop(boolean qd)         { role = qd ? Role.QUICK_DROP : Role.STORAGE; }

    // ── Legacy API ────────────────────────────────────────────────
    public void addAllowedMod(String modId) {
        if (!allowedMods.contains(modId)) allowedMods.add(modId);
        addFilter(FilterMode.ALLOW, FilterType.MOD_ID, modId);
    }
    public void removeAllowedMod(String modId) {
        allowedMods.remove(modId);
        filters.removeIf(f -> f.type == FilterType.MOD_ID && f.value.equals(modId));
    }
    public void addAllowedItem(String itemId) {
        if (!allowedItems.contains(itemId)) allowedItems.add(itemId);
        addFilter(FilterMode.ALLOW, FilterType.EXACT_ID, itemId);
    }
    public void removeAllowedItem(String itemId) {
        allowedItems.remove(itemId);
        filters.removeIf(f -> f.type == FilterType.EXACT_ID && f.value.equals(itemId));
    }
    public void addCraftRule(int a, int b) { /* legacy no-op */ }
    public void addFilter(FilterMode mode, FilterType type, String value) {
        filters.add(new ItemFilter(mode, type, value));
    }
    public void addCraftRule(CraftRule rule) { craftRulesNew.add(rule); }

    // ── Кэш содержимого ──────────────────────────────────────────
    public void updateCache(List<ItemStack> contents, int totalSlots) {
        cachedContents.clear();
        int used = 0;
        for (ItemStack s : contents) {
            if (s.isEmpty()) continue;
            cachedContents.merge(ItemKey.type(s), s.getCount(), Integer::sum);
            used++;
        }
        cachedFreeSlots = Math.max(0, totalSlots - used);
        dirty = false;
    }

    public boolean containsItemType(ItemStack stack) {
        return cachedContents.containsKey(ItemKey.type(stack));
    }
    public boolean isEmpty() { return cachedContents.isEmpty(); }
    public boolean hasRoomForStack(ItemStack stack) {
        if (cachedFreeSlots > 0) return true;
        Integer count = cachedContents.get(ItemKey.type(stack));
        return count != null && count < stack.getMaxStackSize();
    }

    // ── Scoring helpers ───────────────────────────────────────────
    public boolean passesAllow(ItemStack stack) {
        List<ItemFilter> allows = filters.stream().filter(f -> f.mode == FilterMode.ALLOW).toList();
        if (allows.isEmpty()) return true;
        for (ItemFilter f : allows) if (matchesFilter(f, stack)) return true;
        return false;
    }
    public boolean passesDeny(ItemStack stack) {
        for (ItemFilter f : filters)
            if (f.mode == FilterMode.DENY && matchesFilter(f, stack)) return true;
        return false;
    }
    public boolean matchesExact(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return filters.stream().anyMatch(f ->
                f.mode == FilterMode.ALLOW && f.type == FilterType.EXACT_ID && f.value.equals(id));
    }
    public boolean matchesMod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String mod = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return filters.stream().anyMatch(f ->
                f.mode == FilterMode.ALLOW && f.type == FilterType.MOD_ID && f.value.equals(mod));
    }
    public boolean matchesCategory(ItemStack stack) {
        return pinnedCategory != null && !stack.isEmpty()
                && ItemSortingEngine.getCategory(stack) == pinnedCategory;
    }
    private boolean matchesFilter(ItemFilter f, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (f.type) {
            case EXACT_ID -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(f.value);
            case MOD_ID   -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(f.value);
            case CATEGORY -> pinnedCategory != null && pinnedCategory.name().equalsIgnoreCase(f.value);
        };
    }

    /**
     * Может ли этот сундук хранить предмет?
     * STORAGE без фильтров — принимает всё как overflow-fallback.
     */
    public boolean canStoreItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (role == Role.TRASH || role == Role.QUICK_DROP) return false;
        if (passesDeny(stack)) return false;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String modId  = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();

        if (!allowedItems.isEmpty() && allowedItems.contains(itemId)) return true;
        if (!allowedMods.isEmpty()  && allowedMods.contains(modId))   return true;
        if (!filters.isEmpty()) return passesAllow(stack);

        return role == Role.STORAGE || role == Role.OVERFLOW;
    }

    // ── Сериализация / Десериализация ─────────────────────────────
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", position.getX());
        tag.putInt("Y", position.getY());
        tag.putInt("Z", position.getZ());
        tag.putString("ChestName", chestName);
        tag.putString("Role", role.name());
        tag.putInt("Priority", priority);
        if (pinnedCategory != null) tag.putString("PinnedCategory", pinnedCategory.name());

        ListTag modsTag = new ListTag();
        for (String m : allowedMods) { CompoundTag t = new CompoundTag(); t.putString("Mod", m); modsTag.add(t); }
        tag.put("AllowedMods", modsTag);

        ListTag itemsTag = new ListTag();
        for (String i : allowedItems) { CompoundTag t = new CompoundTag(); t.putString("Item", i); itemsTag.add(t); }
        tag.put("AllowedItems", itemsTag);

        ListTag filtersTag = new ListTag();
        for (ItemFilter f : filters) {
            CompoundTag ft = new CompoundTag();
            ft.putString("Mode", f.mode.name()); ft.putString("FType", f.type.name()); ft.putString("Value", f.value);
            filtersTag.add(ft);
        }
        tag.put("Filters", filtersTag);

        ListTag craftTag = new ListTag();
        for (CraftRule cr : craftRulesNew) craftTag.add(cr.serializeNBT());
        tag.put("CraftRules", craftTag);

        return tag;
    }

    public static ChestConfiguration deserializeNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        ChestConfiguration c = new ChestConfiguration(pos);
        c.chestName = tag.getString("ChestName");

        String roleStr = tag.getString("Role");
        if (!roleStr.isEmpty()) { try { c.role = Role.valueOf(roleStr); } catch (Exception ignored) {} }
        // Legacy back-compat
        if (tag.contains("IsQuickDrop") && tag.getBoolean("IsQuickDrop")) c.role = Role.QUICK_DROP;

        c.priority = tag.contains("Priority") ? tag.getInt("Priority") : 50;
        if (tag.contains("PinnedCategory")) {
            try { c.pinnedCategory = ItemCategory.valueOf(tag.getString("PinnedCategory")); } catch (Exception ignored) {}
        }

        ListTag modsTag = tag.getList("AllowedMods", Tag.TAG_COMPOUND);
        for (int i = 0; i < modsTag.size(); i++) c.allowedMods.add(modsTag.getCompound(i).getString("Mod"));

        ListTag itemsTag = tag.getList("AllowedItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) c.allowedItems.add(itemsTag.getCompound(i).getString("Item"));

        ListTag filtersTag = tag.getList("Filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < filtersTag.size(); i++) {
            CompoundTag ft = filtersTag.getCompound(i);
            try {
                c.filters.add(new ItemFilter(FilterMode.valueOf(ft.getString("Mode")),
                        FilterType.valueOf(ft.getString("FType")), ft.getString("Value")));
            } catch (Exception ignored) {}
        }

        ListTag craftTag = tag.getList("CraftRules", Tag.TAG_COMPOUND);
        for (int i = 0; i < craftTag.size(); i++) {
            try { c.craftRulesNew.add(CraftRule.deserializeNBT(craftTag.getCompound(i))); } catch (Exception ignored) {}
        }

        return c;
    }
}
