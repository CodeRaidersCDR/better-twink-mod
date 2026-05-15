package com.minemods.bettertwink.sorting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Ключ предмета с тремя режимами сравнения.
 *
 * STRICT — id + nbtHash (для перекладывания, компактинга).
 * TYPE   — только id (для фильтров «все железные слитки»).
 * MODE задаётся при создании; equals/hashCode учитывают режим.
 */
public final class ItemKey {

    public enum Mode { STRICT, TYPE }

    public final ResourceLocation id;
    public final int nbtHash;
    public final Mode mode;

    private ItemKey(ResourceLocation id, int nbtHash, Mode mode) {
        this.id      = id;
        this.nbtHash = nbtHash;
        this.mode    = mode;
    }

    /** STRICT ключ: id + NBT. */
    public static ItemKey strict(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int nbt = stack.hasTag() ? stack.getTag().hashCode() : 0;
        return new ItemKey(id, nbt, Mode.STRICT);
    }

    /** TYPE ключ: только id. */
    public static ItemKey type(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new ItemKey(id, 0, Mode.TYPE);
    }

    public static ItemKey ofId(ResourceLocation id) {
        return new ItemKey(id, 0, Mode.TYPE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey other)) return false;
        if (!id.equals(other.id)) return false;
        // Two keys are equal if EITHER is TYPE (mode-agnostic by-type match)
        if (mode == Mode.TYPE || other.mode == Mode.TYPE) return true;
        return nbtHash == other.nbtHash;
    }

    @Override
    public int hashCode() {
        // TYPE keys must hash the same as STRICT keys with same id so they collide in Maps
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return mode == Mode.STRICT ? id + "#" + nbtHash : id.toString();
    }
}
