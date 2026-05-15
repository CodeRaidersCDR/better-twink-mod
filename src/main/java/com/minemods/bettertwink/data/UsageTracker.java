package com.minemods.bettertwink.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Singleton that records player and bot inventory interactions and maintains
 * one {@link ItemUsageProfile} per item type.
 *
 * <p>Call sites:
 * <ul>
 *   <li>{@link #recordPlayerWithdraw} — when player takes item from any source (detected via GUI events).</li>
 *   <li>{@link #recordPlayerDeposit} — when player puts item into a chest (detected via GUI events).</li>
 *   <li>{@link #recordBotDeposit} — when bot's WORK_DEPOSIT phase successfully deposits an item.</li>
 * </ul>
 *
 * <p>The tracker is serialised alongside the server configuration so preferences
 * survive client restarts (§7 — persist cache).
 */
public class UsageTracker {

    private static UsageTracker INSTANCE;

    private final Map<ResourceLocation, ItemUsageProfile> profiles = new HashMap<>();

    private UsageTracker() {}

    public static UsageTracker getInstance() {
        if (INSTANCE == null) INSTANCE = new UsageTracker();
        return INSTANCE;
    }

    // ── Recording ─────────────────────────────────────────────────────────

    /** Record a player withdrawal (item taken from any chest / crafting output). */
    public void recordPlayerWithdraw(ItemStack stack, long currentTick) {
        if (stack.isEmpty()) return;
        profileFor(stack).recordAccess(currentTick, stack.getCount());
    }

    /** Record a player manual deposit into a specific chest. */
    public void recordPlayerDeposit(ItemStack stack, BlockPos chest) {
        if (stack.isEmpty()) return;
        profileFor(stack).recordDeposit(chest);
    }

    /** Record a bot deposit (counts as access + deposit at that chest). */
    public void recordBotDeposit(ItemStack stack, BlockPos chest, long currentTick) {
        if (stack.isEmpty()) return;
        ItemUsageProfile p = profileFor(stack);
        p.recordAccess(currentTick, stack.getCount());
        p.recordDeposit(chest);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** @return the profile for the given item, or {@code null} if never tracked. */
    public ItemUsageProfile getProfile(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return profiles.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * @return the chest position the player most often deposits this item into,
     *         or {@code null} if unknown.
     */
    public BlockPos getPreferredChest(ItemStack stack) {
        ItemUsageProfile p = getProfile(stack);
        return p != null ? p.preferredChest : null;
    }

    /**
     * Scoring bonus for the bot's chest selection: +500 if the candidate chest
     * matches the player's preferred chest for this item type.
     */
    public int preferredChestBonus(ItemStack stack, BlockPos candidate) {
        if (candidate == null) return 0;
        BlockPos pref = getPreferredChest(stack);
        return (pref != null && pref.equals(candidate)) ? 500 : 0;
    }

    public int getProfileCount() { return profiles.size(); }

    public Map<ResourceLocation, ItemUsageProfile> getAllProfiles() {
        return Collections.unmodifiableMap(profiles);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<ResourceLocation, ItemUsageProfile> e : profiles.entrySet()) {
            // Replace ':' with '|' to use as NBT key (colons are reserved in some parsers)
            String key = e.getKey().toString().replace(':', '|');
            tag.put(key, e.getValue().serializeNBT());
        }
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        profiles.clear();
        for (String key : tag.getAllKeys()) {
            try {
                ResourceLocation id = new ResourceLocation(key.replace('|', ':'));
                profiles.put(id, ItemUsageProfile.deserializeNBT(tag.getCompound(key)));
            } catch (Exception ignored) {}
        }
    }

    public void clear() {
        profiles.clear();
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private ItemUsageProfile profileFor(ItemStack stack) {
        return profiles.computeIfAbsent(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                k -> new ItemUsageProfile());
    }
}
