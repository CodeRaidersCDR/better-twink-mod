package com.minemods.bettertwink.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Одно правило автокрафта.
 */
public class CraftRule {

    public enum Workstation { INVENTORY_2x2, CRAFTING_TABLE }
    public enum Direction   { COMPACT, EXPAND }

    public ResourceLocation triggerItem;   // minecraft:iron_ingot
    public ResourceLocation resultItem;    // minecraft:iron_block
    public ResourceLocation recipeId;      // minecraft:iron_block
    public int  threshold;                 // крафтить когда total >= threshold
    public int  keepMinimum;              // оставить N исходных
    public Direction  direction;
    public Workstation workstation;
    public BlockPos workstationPos;        // null → инвентарь 2x2
    public boolean enabled;

    public CraftRule(ResourceLocation triggerItem, ResourceLocation resultItem,
                     ResourceLocation recipeId, int threshold, int keepMinimum,
                     Direction direction, Workstation workstation) {
        this.triggerItem  = triggerItem;
        this.resultItem   = resultItem;
        this.recipeId     = recipeId;
        this.threshold    = threshold;
        this.keepMinimum  = keepMinimum;
        this.direction    = direction;
        this.workstation  = workstation;
        this.enabled      = true;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Trigger",     triggerItem.toString());
        tag.putString("Result",      resultItem.toString());
        tag.putString("Recipe",      recipeId.toString());
        tag.putInt("Threshold",      threshold);
        tag.putInt("KeepMinimum",    keepMinimum);
        tag.putString("Direction",   direction.name());
        tag.putString("Workstation", workstation.name());
        if (workstationPos != null) {
            tag.putInt("WX", workstationPos.getX());
            tag.putInt("WY", workstationPos.getY());
            tag.putInt("WZ", workstationPos.getZ());
        }
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static CraftRule deserializeNBT(CompoundTag tag) {
        CraftRule r = new CraftRule(
                new ResourceLocation(tag.getString("Trigger")),
                new ResourceLocation(tag.getString("Result")),
                new ResourceLocation(tag.getString("Recipe")),
                tag.getInt("Threshold"),
                tag.getInt("KeepMinimum"),
                Direction.valueOf(tag.getString("Direction")),
                Workstation.valueOf(tag.getString("Workstation"))
        );
        r.enabled = tag.getBoolean("Enabled");
        if (tag.contains("WX")) {
            r.workstationPos = new BlockPos(tag.getInt("WX"), tag.getInt("WY"), tag.getInt("WZ"));
        }
        return r;
    }
}
