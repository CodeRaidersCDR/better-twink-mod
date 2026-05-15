package com.minemods.bettertwink.sorting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.core.BlockPos;
import com.minemods.bettertwink.data.ChestConfiguration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Основной класс логики сортировки предметов
 */
public class ItemSortingEngine {
    private static ItemSortingEngine INSTANCE;
    
    private Map<BlockPos, List<ItemStack>> chestInventories;
    private Queue<SortingTask> sortingQueue;
    private boolean isRunning;

    private ItemSortingEngine() {
        this.chestInventories = new HashMap<>();
        this.sortingQueue = new LinkedList<>();
        this.isRunning = false;
    }

    public static ItemSortingEngine getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ItemSortingEngine();
        }
        return INSTANCE;
    }

    /**
     * Сканирует сундуки и определяет, какие предметы нужно перемещать
     */
    public void analyzeSortingNeeds(Map<BlockPos, ChestConfiguration> configuredChests, 
                                   Map<BlockPos, List<ItemStack>> currentInventories) {
        sortingQueue.clear();

        for (Map.Entry<BlockPos, List<ItemStack>> entry : currentInventories.entrySet()) {
            BlockPos pos = entry.getKey();
            List<ItemStack> items = entry.getValue();
            ChestConfiguration config = configuredChests.get(pos);

            if (config == null) continue;

            // Анализируем каждый предмет в сундуке
            for (ItemStack item : items) {
                if (shouldMoveItem(item, config, configuredChests)) {
                    // Ищем правильное место для предмета
                    BlockPos targetChest = findTargetChest(item, configuredChests);
                    if (targetChest != null && !targetChest.equals(pos)) {
                        sortingQueue.add(new SortingTask(pos, targetChest, item.copy()));
                    }
                }
            }
        }
    }

    /**
     * Проверяет, должен ли быть перемещен этот предмет из сундука
     */
    private boolean shouldMoveItem(ItemStack item, ChestConfiguration currentChest,
                                   Map<BlockPos, ChestConfiguration> allChests) {
        // Если это сундук быстрого сброса, все предметы должны сортироваться
        if (currentChest.isQuickDrop()) {
            return true;
        }

        // Если сундук настроен, проверяем, может ли он хранить этот предмет
        if (!currentChest.getAllowedMods().isEmpty() || !currentChest.getAllowedItems().isEmpty()) {
            return !currentChest.canStoreItem(item);
        }

        return false;
    }

    /**
     * Находит правильный сундук для размещения предмета
     */
    private BlockPos findTargetChest(ItemStack item, Map<BlockPos, ChestConfiguration> availableChests) {
        for (Map.Entry<BlockPos, ChestConfiguration> entry : availableChests.entrySet()) {
            ChestConfiguration config = entry.getValue();
            
            // Не размещаем в сундуках быстрого сброса
            if (config.isQuickDrop()) continue;
            
            if (config.canStoreItem(item)) {
                return entry.getKey();
            }
        }
        
        return null;
    }

    /**
     * Получает следующую задачу сортировки
     */
    public SortingTask getNextTask() {
        return sortingQueue.poll();
    }

    /**
     * Проверяет, есть ли задачи в очереди
     */
    public boolean hasPendingTasks() {
        return !sortingQueue.isEmpty();
    }

    /**
     * Получает количество задач в очереди
     */
    public int getPendingTaskCount() {
        return sortingQueue.size();
    }

    /**
     * Очищает очередь задач
     */
    public void clearQueue() {
        sortingQueue.clear();
    }

    /**
     * Запускает процесс сортировки
     */
    public void startSorting() {
        isRunning = true;
    }

    /**
     * Останавливает процесс сортировки
     */
    public void stopSorting() {
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    // ========== Category-based auto-sorting ====================

    public enum ItemCategory {
        FOOD, WEAPONS, ARMOR, TOOLS,
        BUILDING_BLOCKS, REDSTONE, FARMING,
        ENCHANTING, MATERIALS, DECORATION, MISC
    }

    /** Classify an ItemStack into a storage category. */
    public static ItemCategory getCategory(ItemStack stack) {
        if (stack.isEmpty()) return ItemCategory.MISC;
        Item item = stack.getItem();
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();

        // --- API-based (most reliable) ---
        if (item.isEdible()) return ItemCategory.FOOD;
        if (item instanceof ArmorItem)   return ItemCategory.ARMOR;
        if (item instanceof SwordItem)   return ItemCategory.WEAPONS;
        if (item instanceof DiggerItem)  return ItemCategory.TOOLS;   // pickaxe, axe, shovel, hoe
        if (item instanceof BowItem || item instanceof CrossbowItem) return ItemCategory.WEAPONS;

        // --- Enchanting ---
        if (id.equals("enchanted_book") || id.equals("book") || id.equals("writable_book")
                || id.equals("knowledge_book") || id.equals("experience_bottle")
                || id.equals("lapis_lazuli") || id.equals("name_tag"))
            return ItemCategory.ENCHANTING;

        // --- Weapons (non-class) ---
        if (id.equals("trident") || id.equals("shield")) return ItemCategory.WEAPONS;

        // --- Tools (non-class) ---
        if (id.equals("fishing_rod") || id.equals("shears") || id.equals("flint_and_steel")
                || id.equals("compass") || id.equals("clock") || id.equals("spyglass")
                || id.equals("lead") || id.equals("brush") || id.equals("fire_charge"))
            return ItemCategory.TOOLS;

        // --- Redstone ---
        if (id.startsWith("redstone") || id.endsWith("_button") || id.endsWith("_pressure_plate")
                || id.contains("piston") || id.equals("observer") || id.equals("repeater")
                || id.equals("comparator") || id.equals("daylight_detector") || id.equals("target")
                || id.equals("dispenser") || id.equals("dropper") || id.equals("hopper")
                || id.contains("_rail") || id.equals("rail") || id.equals("tripwire_hook")
                || id.equals("lever") || id.equals("tnt") || id.equals("note_block")
                || id.equals("lectern") || id.equals("sculk_sensor")
                || id.equals("calibrated_sculk_sensor"))
            return ItemCategory.REDSTONE;

        // --- Farming / Nature ---
        if (id.endsWith("_seeds") || id.equals("wheat") || id.equals("carrot")
                || id.equals("potato") || id.equals("beetroot") || id.equals("pumpkin")
                || id.equals("melon") || id.equals("sugar_cane") || id.equals("bamboo")
                || id.equals("cactus") || id.equals("kelp") || id.equals("bone_meal")
                || id.equals("bone") || id.endsWith("_berries") || id.equals("cocoa_beans")
                || id.contains("sapling") || id.equals("brown_mushroom") || id.equals("red_mushroom"))
            return ItemCategory.FARMING;

        // --- Raw materials / crafting ingredients ---
        if (id.endsWith("_ingot") || id.endsWith("_nugget") || id.startsWith("raw_")
                || id.endsWith("_ore") || id.equals("coal") || id.equals("charcoal")
                || id.equals("diamond") || id.equals("emerald") || id.equals("quartz")
                || id.equals("amethyst_shard") || id.equals("netherite_scrap")
                || id.endsWith("_dust") || id.equals("gunpowder") || id.equals("flint")
                || id.equals("string") || id.equals("feather") || id.equals("leather")
                || id.equals("blaze_rod") || id.equals("blaze_powder")
                || id.equals("slime_ball") || id.equals("magma_cream")
                || id.equals("ender_pearl") || id.equals("ender_eye")
                || id.equals("ghast_tear") || id.equals("phantom_membrane")
                || id.equals("rabbit_hide") || id.equals("rabbit_foot")
                || id.equals("honeycomb") || id.equals("ink_sac") || id.equals("glow_ink_sac")
                || id.equals("prismarine_shard") || id.equals("prismarine_crystals")
                || id.equals("nether_star"))
            return ItemCategory.MATERIALS;

        // --- Block items: decoration vs building ---
        if (item instanceof BlockItem) {
            if (isDecorationBlock(id)) return ItemCategory.DECORATION;
            return ItemCategory.BUILDING_BLOCKS;
        }

        // --- Non-block decoration ---
        if (id.contains("item_frame") || id.equals("painting") || id.equals("flower_pot"))
            return ItemCategory.DECORATION;

        // --- Armor fallback (non-ArmorItem subclasses) ---
        if (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings")
                || id.endsWith("_boots") || id.endsWith("_horse_armor") || id.equals("elytra"))
            return ItemCategory.ARMOR;

        return ItemCategory.MISC;
    }

    private static boolean isDecorationBlock(String id) {
        return id.contains("torch") || id.contains("lantern") || id.contains("candle")
                || id.contains("banner") || id.contains("painting") || id.contains("item_frame")
                || id.contains("carpet") || id.contains("flower") || id.contains("tulip")
                || id.contains("orchid") || id.contains("bluet") || id.contains("daisy")
                || id.equals("dandelion") || id.equals("poppy") || id.equals("allium")
                || id.equals("lilac") || id.equals("rose_bush") || id.equals("peony")
                || id.equals("sunflower") || id.equals("cornflower") || id.equals("oxeye_daisy")
                || id.equals("azure_bluet") || id.equals("lily_of_the_valley")
                || (id.contains("coral") && !id.endsWith("_block"))
                || id.equals("lily_pad") || id.equals("vine") || id.equals("glow_lichen")
                || id.equals("hanging_roots") || id.contains("fern") || id.equals("dead_bush")
                || (id.contains("mushroom") && !id.endsWith("_block"))
                || id.contains("leaves");
    }

    /** Convenience overload without quickDrop info. */
    public void analyzeAutoSorting(Map<BlockPos, List<ItemStack>> inventories) {
        analyzeAutoSorting(inventories, Collections.emptySet());
    }

    /**
     * Авто-сортировка по категориям.
     * QuickDrop-сундуки — это ВСЕГДА источники: их содержимое разливается по
     * категорийным сундукам. Они не участвуют в назначении категорий и не
     * принимают предметы.
     *
     * 1. Каждый НЕ-QuickDrop сундук получает категорию по доминирующему типу предметов.
     * 2. Пустые (не-QD) сундуки назначаются категориям без выделенного сундука.
     * 3. Предметы не той категории в не-QD сундуках → переместить.
     * 4. ВСЕ предметы в QD-сундуках → переместить в нужный категорийный сундук.
     */
    public void analyzeAutoSorting(Map<BlockPos, List<ItemStack>> inventories,
                                   Set<BlockPos> quickDropChests) {
        sortingQueue.clear();
        if (inventories.isEmpty()) return;

        // --- Step 1: score each NON-QD chest by category ---
        Map<BlockPos, Map<ItemCategory, Integer>> chestScores = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, List<ItemStack>> e : inventories.entrySet()) {
            if (quickDropChests.contains(e.getKey())) continue; // QD chests don't get a category
            Map<ItemCategory, Integer> scores = new EnumMap<>(ItemCategory.class);
            for (ItemStack s : e.getValue()) scores.merge(getCategory(s), s.getCount(), Integer::sum);
            chestScores.put(e.getKey(), scores);
        }

        // --- Step 2: assign each non-QD chest to its dominant category ---
        Map<BlockPos, ItemCategory> chestAssignment = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Map<ItemCategory, Integer>> e : chestScores.entrySet()) {
            ItemCategory dominant = e.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(ItemCategory.MISC);
            chestAssignment.put(e.getKey(), dominant);
        }

        // --- Step 3: reverse map category -> chests ---
        Map<ItemCategory, List<BlockPos>> categoryChests = new EnumMap<>(ItemCategory.class);
        for (Map.Entry<BlockPos, ItemCategory> e : chestAssignment.entrySet()) {
            categoryChests.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        // --- Step 4: assign empty non-QD chests to categories that need a chest ---
        Map<ItemCategory, Integer> globalCatCount = new EnumMap<>(ItemCategory.class);
        for (Map.Entry<BlockPos, List<ItemStack>> e : inventories.entrySet()) {
            if (quickDropChests.contains(e.getKey())) continue;
            for (ItemStack s : e.getValue()) globalCatCount.merge(getCategory(s), s.getCount(), Integer::sum);
        }
        // Also count items in QD chests to avoid leaving them without a destination
        for (BlockPos qd : quickDropChests) {
            for (ItemStack s : inventories.getOrDefault(qd, List.of()))
                globalCatCount.merge(getCategory(s), s.getCount(), Integer::sum);
        }

        List<BlockPos> emptyChests = chestAssignment.entrySet().stream()
                .filter(e -> e.getValue() == ItemCategory.MISC
                        && inventories.getOrDefault(e.getKey(), List.of()).isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        globalCatCount.entrySet().stream()
                .filter(e -> e.getKey() != ItemCategory.MISC)
                .filter(e -> !categoryChests.containsKey(e.getKey()) || categoryChests.get(e.getKey()).isEmpty())
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    if (!emptyChests.isEmpty()) {
                        BlockPos chest = emptyChests.remove(0);
                        chestAssignment.put(chest, e.getKey());
                        categoryChests.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(chest);
                    }
                });

        // --- Step 5: generate tasks for misplaced items in NON-QD chests ---
        for (Map.Entry<BlockPos, List<ItemStack>> e : inventories.entrySet()) {
            BlockPos src = e.getKey();
            if (quickDropChests.contains(src)) continue; // handled in step 6
            ItemCategory srcCat = chestAssignment.get(src);
            for (ItemStack stack : e.getValue()) {
                ItemCategory itemCat = getCategory(stack);
                if (itemCat == srcCat) continue;
                List<BlockPos> targets = categoryChests.get(itemCat);
                BlockPos tgt = targets == null ? null :
                        targets.stream().filter(p -> !p.equals(src)).findFirst().orElse(null);
                if (tgt == null) continue; // no category chest, leave item in place
                sortingQueue.add(new SortingTask(src, tgt, stack.copy()));
            }
        }

        // --- Step 6: empty ALL QuickDrop chests into category chests ---
        for (BlockPos qd : quickDropChests) {
            for (ItemStack stack : inventories.getOrDefault(qd, List.of())) {
                ItemCategory itemCat = getCategory(stack);
                List<BlockPos> targets = categoryChests.get(itemCat);
                if (targets == null || targets.isEmpty())
                    targets = categoryChests.get(ItemCategory.MISC);
                BlockPos tgt = targets == null ? null :
                        targets.stream().filter(p -> !p.equals(qd)).findFirst().orElse(null);
                if (tgt == null) continue; // no suitable chest for this item
                sortingQueue.add(new SortingTask(qd, tgt, stack.copy()));
            }
        }
    }

    /**
     * Представляет одну задачу сортировки
     */
    public static class SortingTask {
        public final BlockPos sourceChest;
        public final BlockPos targetChest;
        public final ItemStack item;

        public SortingTask(BlockPos sourceChest, BlockPos targetChest, ItemStack item) {
            this.sourceChest = sourceChest;
            this.targetChest = targetChest;
            this.item = item;
        }
    }
}
