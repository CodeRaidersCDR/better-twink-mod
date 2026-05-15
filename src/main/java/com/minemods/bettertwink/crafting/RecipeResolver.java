package com.minemods.bettertwink.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * FIX BUG #6: Wrapper over the client-side recipe manager.
 * Centralises all recipe lookups so every caller gets consistent error handling.
 */
public final class RecipeResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeResolver() {}

    /**
     * Resolves a {@link CraftingRecipe} by its resource location.
     *
     * @param id the recipe resource location (e.g. {@code minecraft:iron_block})
     * @return the recipe, or {@link Optional#empty()} if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public static Optional<CraftingRecipe> resolve(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();
        try {
            Optional<? extends Recipe<?>> opt = mc.level.getRecipeManager().byKey(id);
            return opt.filter(r -> r instanceof CraftingRecipe)
                      .map(r -> (CraftingRecipe) r);
        } catch (Exception e) {
            LOGGER.warn("[RecipeResolver] Failed to resolve recipe {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the number of ingredient slots used by {@code recipe}
     * (empty ingredient slots are excluded).
     */
    public static int ingredientCount(CraftingRecipe recipe) {
        return (int) recipe.getIngredients().stream()
                .filter(ing -> !ing.isEmpty())
                .count();
    }
}
