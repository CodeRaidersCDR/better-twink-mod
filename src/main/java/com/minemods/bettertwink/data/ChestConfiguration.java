package com.minemods.bettertwink.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Представляет один сундук и правила сортировки для него
 */
public class ChestConfiguration {
    private BlockPos position;
    private String chestName;
    private List<String> allowedMods; // Какие моды предметов хранить
    private List<String> allowedItems; // Конкретные предметы
    private List<Integer> craftRules; // Правила крафта (itemId -> craftedItemId)
    private boolean isQuickDrop; // Является ли сундук быстрого сброса

    public ChestConfiguration(BlockPos position) {
        this.position = position;
        this.chestName = "Chest " + position.getX() + ", " + position.getY() + ", " + position.getZ();
        this.allowedMods = new ArrayList<>();
        this.allowedItems = new ArrayList<>();
        this.craftRules = new ArrayList<>();
        this.isQuickDrop = false;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getChestName() {
        return chestName;
    }

    public void setChestName(String name) {
        this.chestName = name;
    }

    public List<String> getAllowedMods() {
        return allowedMods;
    }

    public List<String> getAllowedItems() {
        return allowedItems;
    }

    public List<Integer> getCraftRules() {
        return craftRules;
    }

    public boolean isQuickDrop() {
        return isQuickDrop;
    }

    public void setQuickDrop(boolean quickDrop) {
        isQuickDrop = quickDrop;
    }

    public void addAllowedMod(String modId) {
        if (!allowedMods.contains(modId)) {
            allowedMods.add(modId);
        }
    }

    public void removeAllowedMod(String modId) {
        allowedMods.remove(modId);
    }

    public void addAllowedItem(String itemId) {
        if (!allowedItems.contains(itemId)) {
            allowedItems.add(itemId);
        }
    }

    public void removeAllowedItem(String itemId) {
        allowedItems.remove(itemId);
    }

    public void addCraftRule(int fromItemId, int toItemId) {
        craftRules.add(fromItemId);
        craftRules.add(toItemId);
    }

    public boolean canStoreItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        String itemId = itemStack.getItem().toString();
        String modId = itemStack.getItem().getCreatorModId(itemStack);

        // Проверка конкретных предметов
        if (!allowedItems.isEmpty() && allowedItems.contains(itemId)) {
            return true;
        }

        // Проверка модов
        if (!allowedMods.isEmpty() && modId != null && allowedMods.contains(modId)) {
            return true;
        }

        return false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", position.getX());
        tag.putInt("Y", position.getY());
        tag.putInt("Z", position.getZ());
        tag.putString("ChestName", chestName);
        tag.putBoolean("IsQuickDrop", isQuickDrop);

        ListTag modsTag = new ListTag();
        for (String mod : allowedMods) {
            CompoundTag modTag = new CompoundTag();
            modTag.putString("Mod", mod);
            modsTag.add(modTag);
        }
        tag.put("AllowedMods", modsTag);

        ListTag itemsTag = new ListTag();
        for (String item : allowedItems) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("Item", item);
            itemsTag.add(itemTag);
        }
        tag.put("AllowedItems", itemsTag);

        return tag;
    }

    public static ChestConfiguration deserializeNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        ChestConfiguration config = new ChestConfiguration(pos);
        
        config.chestName = tag.getString("ChestName");
        config.isQuickDrop = tag.getBoolean("IsQuickDrop");

        ListTag modsTag = tag.getList("AllowedMods", Tag.TAG_COMPOUND);
        for (int i = 0; i < modsTag.size(); i++) {
            CompoundTag modTag = modsTag.getCompound(i);
            config.allowedMods.add(modTag.getString("Mod"));
        }

        ListTag itemsTag = tag.getList("AllowedItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) {
            CompoundTag itemTag = itemsTag.getCompound(i);
            config.allowedItems.add(itemTag.getString("Item"));
        }

        return config;
    }
}
