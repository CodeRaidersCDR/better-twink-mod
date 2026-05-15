package com.minemods.bettertwink.client.bot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import com.minemods.bettertwink.config.BetterTwinkConfig;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;
import com.minemods.bettertwink.pathfinding.PathFinder;
import com.minemods.bettertwink.sorting.ItemSortingEngine;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * от-сортировщик: сканирует сундуки, анализирует содержимое,
 * перемещает предметы между сундуками согласно правилам.
 *
 * азы: IDLE -> SCAN_NAVIGATE -> SCAN_WAIT_OPEN -> SCAN_READ
 *       -> PLANNING
 *       -> WORK_NAV_SRC -> WORK_OPEN_SRC -> WORK_TAKE
 *       -> WORK_NAV_DST -> WORK_OPEN_DST -> WORK_DEPOSIT
 *       -> (следующая задача или IDLE)
 */
public class SortingBotController {

    private static SortingBotController INSTANCE;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Minecraft MC = Minecraft.getInstance();

    // ── убличные состояния (для GUI) ──────────────────────────
    public enum BotState { IDLE, ANALYZING, NAVIGATING, WORKING, CRAFTING }

    // ── нутренние фазы ──────────────────────────────────────
    private enum Phase {
        IDLE,
        SCAN_NAVIGATE, SCAN_WAIT_OPEN, SCAN_READ,
        PLANNING,
        WORK_NAV_SRC, WORK_OPEN_SRC, WORK_TAKE,
        WORK_NAV_DST, WORK_OPEN_DST, WORK_DEPOSIT, WORK_COMPACT_DST
    }

    private Phase phase = Phase.IDLE;
    private boolean isRunning = false;
    private int waitTicks = 0;

    // анные сканирования
    private final Map<BlockPos, List<ItemStack>> scannedInventories = new LinkedHashMap<>();
    private List<BlockPos> scanQueue = new ArrayList<>();
    private int scanIndex = 0;
    private int openRetries = 0;

    // Данные рабочей фазы — батчевая система
    // Батч = один визит к источнику (загрузить весь инвентарь) + развоз по всем точкам назначения
    private final Queue<SortingBatch> batchQueue = new LinkedList<>();
    private SortingBatch currentBatch = null;
    private int srcSlot    = 0;  // текущий слот источника при WORK_TAKE
    private int depositSlot = 0; // текущий слот инвентаря при WORK_DEPOSIT

    // Compact phase state: consolidate partial stacks inside destination chest
    // compactSubState: 0=scan, 1=after-pickup (waiting for PICKUP_ALL), 2=after-pickup-all (waiting for place)
    private int compactSubState   = 0;
    private int compactPickedSlot = -1;
    private int compactTargetSlot = -1; // non-empty slot used for PICKUP_ALL
    private int compactIterations = 0;  // guards against infinite compact loops

    /**
     * Батч содержит один визит к источнику + несколько визитов к приёмникам.
     * allItemIds = объединение всех delivery.
     */
    private static class SortingBatch {
        final BlockPos sourceChest;
        // target -> list of item registry IDs to deposit there
        final Map<BlockPos, List<String>> deliveryMap;
        final List<BlockPos> deliveryOrder;
        int deliveryIndex = 0;

        SortingBatch(BlockPos src, Map<BlockPos, List<String>> map) {
            this.sourceChest   = src;
            this.deliveryMap   = map;
            this.deliveryOrder = new ArrayList<>(map.keySet());
        }

        BlockPos currentTarget() {
            return deliveryIndex < deliveryOrder.size() ? deliveryOrder.get(deliveryIndex) : null;
        }

        List<String> currentItemIds() {
            BlockPos t = currentTarget();
            return t != null ? deliveryMap.getOrDefault(t, Collections.emptyList()) : Collections.emptyList();
        }

        Set<String> allItemIds() {
            Set<String> all = new HashSet<>();
            deliveryMap.values().forEach(all::addAll);
            return all;
        }

        /** Идти к следующей точке доставки. @return true если есть следующая. */
        boolean advanceDelivery() {
            deliveryIndex++;
            return deliveryIndex < deliveryOrder.size();
        }
    }

    // Периодическое пере-сканирование
    private int idleTickCount = 0;
    private static final int RESCAN_TICKS = 400; // 20 сек

    // Навигация по пути (A*)
    private List<BlockPos> currentPath      = null;
    private int pathNodeIndex               = 0;
    private BlockPos lastNavTarget          = null;
    private int stuckCounter                = 0;
    private int stuckResets                 = 0;   // how many times path was reset for current target
    private Vec3 lastPlayerPos              = null;
    private static final double REACH_DIST  = 2.8;  // расстояние для взаимодействия
    private static final double NODE_RADIUS = 0.6;  // «достиг узла»
    private static final int MAX_STUCK_RESETS = 5;  // give up after this many resets

    // Состояние движения — читается BotTickHandler в Phase.START
    private volatile float  navDesiredYaw   = 0f;
    private volatile float  navDesiredXRot  = 0f;
    private volatile boolean navDesiredJump = false;
    private volatile boolean navActive      = false; // бот сейчас навигирует

    // ──────────────────────────────────────────────────────────

    private SortingBotController() {}

    public static SortingBotController getInstance() {
        if (INSTANCE == null) INSTANCE = new SortingBotController();
        return INSTANCE;
    }

    // ===================== PUBLIC API ========================

    public void startBot() {
        if (isRunning) return;
        isRunning = true;
        idleTickCount = 0;
        startScanPhase();
        LOGGER.info("[BetterTwink] Bot started");
    }

    public void stopBot() {
        isRunning = false;
        phase = Phase.IDLE;
        waitTicks = 0;
        batchQueue.clear();
        currentBatch = null;
        currentPath = null;
        lastNavTarget = null;
        navActive = false;
        navDesiredJump = false;
        if (MC.player != null && MC.player.containerMenu != MC.player.inventoryMenu) {
            MC.player.closeContainer();
        }
        LOGGER.info("[BetterTwink] Bot stopped");
    }

    /** ызывается каждый клиентский тик из BotTickHandler */
    public void update(Player player, Level level) {
        if (!isRunning || !BetterTwinkConfig.ENABLED.get()) return;
        if (player == null || level == null) return;
        if (waitTicks > 0) { waitTicks--; return; }

        switch (phase) {
            case IDLE           -> tickIdle();
            case SCAN_NAVIGATE  -> tickScanNavigate(player, level);
            case SCAN_WAIT_OPEN -> tickScanWaitOpen(player);
            case SCAN_READ      -> tickScanRead(player);
            case PLANNING       -> tickPlanning();
            case WORK_NAV_SRC   -> tickWorkNavSrc(player, level);
            case WORK_OPEN_SRC  -> tickWorkOpenSrc(player);
            case WORK_TAKE      -> tickWorkTake(player);
            case WORK_NAV_DST       -> tickWorkNavDst(player, level);
            case WORK_OPEN_DST      -> tickWorkOpenDst(player);
            case WORK_DEPOSIT       -> tickWorkDeposit(player);
            case WORK_COMPACT_DST   -> tickWorkCompact(player);
        }
    }

    public BotState getCurrentState() {
        if (!isRunning) return BotState.IDLE;
        return switch (phase) {
            case IDLE -> BotState.IDLE;
            case SCAN_NAVIGATE, SCAN_WAIT_OPEN, SCAN_READ, PLANNING -> BotState.ANALYZING;
            case WORK_NAV_SRC, WORK_NAV_DST -> BotState.NAVIGATING;
            default -> BotState.WORKING;
        };
    }

    public boolean isRunning()      { return isRunning; }
    public int getRemainingTasks()  { return batchQueue.size() + (currentBatch != null ? 1 : 0); }

    public BlockPos getCurrentTarget() {
        if (currentBatch != null) {
            if (phase == Phase.WORK_NAV_SRC || phase == Phase.WORK_OPEN_SRC || phase == Phase.WORK_TAKE)
                return currentBatch.sourceChest;
            return currentBatch.currentTarget() != null ? currentBatch.currentTarget() : currentBatch.sourceChest;
        }
        if (scanIndex < scanQueue.size()) return scanQueue.get(scanIndex);
        return null;
    }

    /** Returns the current computed A* path for rendering. May be null. */
    public List<BlockPos> getCurrentPath() { return currentPath; }

    // ===================== DEBUG FILE LOG =====================

    private static final String LOG_FILE = "logs/bettertwink-debug.log";

    private void debugLog(String msg) {
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + msg;
        LOGGER.info("[BetterTwink-DEBUG] {}", msg);
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new java.io.File(MC.gameDirectory, LOG_FILE), true))) {
            pw.println(line);
        } catch (IOException ignored) {}
    }

    // ===================== IDLE ================================

    private void tickIdle() {
        if (++idleTickCount >= RESCAN_TICKS) {
            idleTickCount = 0;
            startScanPhase();
        }
    }

    // ===================== SCAN PHASE ==========================

    private void startScanPhase() {
        scannedInventories.clear();
        scanQueue = collectChestPositions();
        scanIndex = 0;
        stuckResets = 0;
        if (scanQueue.isEmpty()) {
            notify("\u00a7e[BetterTwink] \u00a7fет зарегистрированных сундуков. обавьте их через 'Select Chests'.");
            phase = Phase.IDLE;
        } else {
            notify("\u00a7e[BetterTwink] \u00a7fСканирую " + scanQueue.size() + " сундуков...");
            phase = Phase.SCAN_NAVIGATE;
        }
    }

    private List<BlockPos> collectChestPositions() {
        List<BlockPos> list = new ArrayList<>();
        for (ChestConfiguration c : ConfigurationManager.getInstance()
                .getCurrentServerConfig().getChests().values()) {
            list.add(c.getPosition());
        }
        return list;
    }

    private void tickScanNavigate(Player player, Level level) {
        if (scanIndex >= scanQueue.size()) {
            phase = Phase.PLANNING;
            return;
        }
        // Give up on unreachable chest and move to next
        if (stuckResets >= MAX_STUCK_RESETS) {
            LOGGER.warn("[BetterTwink] Cannot reach scan chest {}, skipping",
                    scanQueue.get(scanIndex).toShortString());
            stuckResets = 0;
            scanIndex++;
            phase = Phase.SCAN_NAVIGATE;
            return;
        }
        BlockPos target = scanQueue.get(scanIndex);
        if (navigateTo(player, level, target)) {
            openRetries = 0;
            requestOpenContainer(target);
            waitTicks = 7;
            phase = Phase.SCAN_WAIT_OPEN;
        }
    }

    private void tickScanWaitOpen(Player player) {
        if (isContainerOpen(player)) {
            // CRITICAL: wait extra ticks for ContainerSetContentPacket (slot data arrives
            //           one tick AFTER the container-open packet, or more on high latency).
            waitTicks = 4;
            phase = Phase.SCAN_READ;
        } else if (++openRetries < 3) {
            // овторная попытка открыть
            requestOpenContainer(scanQueue.get(scanIndex));
            waitTicks = 7;
        } else {
            LOGGER.warn("[BetterTwink] е удалось открыть сундук на {}, пропускаю", scanQueue.get(scanIndex));
            scanIndex++;
            phase = Phase.SCAN_NAVIGATE;
        }
    }

    private void tickScanRead(Player player) {
        if (!isContainerOpen(player)) {
            scanIndex++;
            phase = Phase.SCAN_NAVIGATE;
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        // Слоты сундука — всё кроме последних 36 слотов инвентаря игрока
        int chestSlots = menu.slots.size() - 36;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty()) items.add(s.copy());
        }
        scannedInventories.put(scanQueue.get(scanIndex), items);

        notify("\u00a7b[Скан " + (scanIndex + 1) + "/" + scanQueue.size() + "] \u00a7f"
                + scanQueue.get(scanIndex).toShortString() + " \u00a77→ " + items.size() + " стаков");
        LOGGER.info("[BetterTwink] Chest {} scanned: {} stacks", scanQueue.get(scanIndex).toShortString(), items.size());

        player.closeContainer();
        waitTicks = 4;
        scanIndex++;
        phase = Phase.SCAN_NAVIGATE;
    }

    // ===================== PLANNING ============================

    private void tickPlanning() {
        Map<String, ChestConfiguration> confMap = ConfigurationManager.getInstance()
                .getCurrentServerConfig().getChests();
        Map<BlockPos, ChestConfiguration> configByPos = new HashMap<>();
        for (ChestConfiguration c : confMap.values()) configByPos.put(c.getPosition(), c);

        // QuickDrop chests are always SOURCES — bot empties them into category chests
        Set<BlockPos> quickDropChests = configByPos.entrySet().stream()
                .filter(e -> e.getValue().isQuickDrop())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        boolean anyExplicitRules = configByPos.values().stream()
                .anyMatch(c -> !c.getAllowedMods().isEmpty() || !c.getAllowedItems().isEmpty());

        int totalStacks = scannedInventories.values().stream().mapToInt(List::size).sum();
        debugLog("PLANNING: " + scannedInventories.size() + " chests, " + totalStacks
                + " stacks, quickDrop=" + quickDropChests.size() + ", explicitRules=" + anyExplicitRules);
        for (Map.Entry<BlockPos, List<ItemStack>> e : scannedInventories.entrySet()) {
            debugLog("  chest " + e.getKey().toShortString() + " -> " + e.getValue().size() + " stacks");
        }

        ItemSortingEngine engine = ItemSortingEngine.getInstance();
        if (anyExplicitRules) {
            engine.analyzeSortingNeeds(configByPos, scannedInventories);
            if (!engine.hasPendingTasks()) {
                debugLog("PLANNING: explicit rules -> 0 tasks, using auto-sort");
                engine.analyzeAutoSorting(scannedInventories, quickDropChests);
            }
        } else {
            engine.analyzeAutoSorting(scannedInventories, quickDropChests);
        }

        // Group tasks by source chest -> (target -> item IDs) to form batches
        Map<BlockPos, Map<BlockPos, List<String>>> batchData = new LinkedHashMap<>();
        while (engine.hasPendingTasks()) {
            ItemSortingEngine.SortingTask t = engine.getNextTask();
            String itemId = BuiltInRegistries.ITEM.getKey(t.item.getItem()).toString();
            batchData
                    .computeIfAbsent(t.sourceChest, k -> new LinkedHashMap<>())
                    .computeIfAbsent(t.targetChest, k -> new ArrayList<>())
                    .add(itemId);
        }

        batchQueue.clear();
        for (Map.Entry<BlockPos, Map<BlockPos, List<String>>> e : batchData.entrySet()) {
            batchQueue.add(new SortingBatch(e.getKey(), e.getValue()));
        }

        // --- Also deliver items already in player inventory ---
        // Build category->chest map from scanned inventories (same dominant-category logic)
        Map<ItemSortingEngine.ItemCategory, BlockPos> catToChest = new EnumMap<>(ItemSortingEngine.ItemCategory.class);
        for (Map.Entry<BlockPos, List<ItemStack>> ce : scannedInventories.entrySet()) {
            if (quickDropChests.contains(ce.getKey())) continue;
            Map<ItemSortingEngine.ItemCategory, Integer> scores = new EnumMap<>(ItemSortingEngine.ItemCategory.class);
            for (ItemStack s : ce.getValue()) scores.merge(ItemSortingEngine.getCategory(s), s.getCount(), Integer::sum);
            if (!scores.isEmpty()) {
                ItemSortingEngine.ItemCategory dominant = scores.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).get().getKey();
                catToChest.putIfAbsent(dominant, ce.getKey());
            }
        }
        Map<BlockPos, List<String>> playerDeliveries = new LinkedHashMap<>();
        for (ItemStack stack : MC.player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ItemSortingEngine.ItemCategory cat = ItemSortingEngine.getCategory(stack);
            BlockPos target = catToChest.get(cat);
            if (target == null) target = catToChest.get(ItemSortingEngine.ItemCategory.MISC);
            if (target == null) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            playerDeliveries.computeIfAbsent(target, k -> new ArrayList<>()).add(itemId);
        }
        if (!playerDeliveries.isEmpty()) {
            batchQueue.add(new SortingBatch(null, playerDeliveries));
        }

        debugLog("PLANNING: " + batchQueue.size() + " batches");
        for (SortingBatch b : batchQueue) {
            debugLog("  BATCH src=" + (b.sourceChest == null ? "PLAYER_INV" : b.sourceChest.toShortString()) + " -> " + b.deliveryOrder.size() + " destinations");
            for (BlockPos dst : b.deliveryOrder) {
                debugLog("    dst=" + dst.toShortString() + " items=" + b.deliveryMap.get(dst));
            }
        }
        LOGGER.info("[BetterTwink] Planning done: {} batches", batchQueue.size());

        if (batchQueue.isEmpty()) {
            notify("\u00a7a[BetterTwink] \u00a7fВсё отсортировано! (просканировано стаков: " + totalStacks + ") Жду " + (RESCAN_TICKS / 20) + " сек...");
            phase = Phase.IDLE;
            idleTickCount = 0;
        } else {
            notify("\u00a7e[BetterTwink] \u00a7fПеремещений: " + batchQueue.size() + " батчей (источников).");
            currentBatch = batchQueue.poll();
            stuckResets = 0;
            phase = Phase.WORK_NAV_SRC;
        }
    }

    // ===================== WORK: SOURCE ========================

    private void tickWorkNavSrc(Player player, Level level) {
        if (currentBatch.sourceChest == null) {
            // Items already in player inventory — skip source navigation/open/take
            srcSlot = 0;
            startDeliveries(player);
            return;
        }
        if (stuckResets >= MAX_STUCK_RESETS) {
            LOGGER.warn("[BetterTwink] Cannot reach source chest {}, skipping batch",
                    currentBatch.sourceChest.toShortString());
            stuckResets = 0;
            advanceBatch();
            return;
        }
        if (navigateTo(player, level, currentBatch.sourceChest)) {
            openRetries = 0;
            requestOpenContainer(currentBatch.sourceChest);
            waitTicks = 7;
            phase = Phase.WORK_OPEN_SRC;
        }
    }

    private void tickWorkOpenSrc(Player player) {
        if (isContainerOpen(player)) {
            waitTicks = 4; // wait for ContainerSetContentPacket
            srcSlot = 0;
            phase = Phase.WORK_TAKE;
        } else if (++openRetries < 3) {
            requestOpenContainer(currentBatch.sourceChest);
            waitTicks = 7;
        } else {
            debugLog("WORK_OPEN_SRC: failed to open " + currentBatch.sourceChest.toShortString());
            advanceBatch();
        }
    }

    /**
     * Батчевая загрузка: берём ШИФТ-кликом ВСЕ предметы, которые есть в списке доставок.
     * Не делаем раунд-трип на каждый тип предмета — грузимся по полному.
     */
    private void tickWorkTake(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!isContainerOpen(player)) {
            debugLog("WORK_TAKE: container closed, starting deliveries");
            startDeliveries(player);
            return;
        }
        int chestSlots = menu.slots.size() - 36;
        Set<String> wantedIds = currentBatch.allItemIds();

        while (srcSlot < chestSlots) {
            ItemStack s = menu.getSlot(srcSlot).getItem();
            if (!s.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                if (wantedIds.contains(id)) {
                    int slot = srcSlot++;
                    debugLog("WORK_TAKE: slot " + slot + " = " + id);
                    MC.gameMode.handleInventoryMouseClick(
                            menu.containerId, slot, 0, ClickType.QUICK_MOVE, MC.player);
                    waitTicks = getTransferTicks();
                    return;
                }
            }
            srcSlot++;
        }
        // Сканировани все слоты — закрываем источник и развозим
        debugLog("WORK_TAKE: done loading, closing source chest");
        player.closeContainer();
        waitTicks = 3;
        startDeliveries(player);
    }

    // ===================== WORK: DESTINATION ===================

    /** Перейти к следующей точке, скипнув те, для которых нет предметов в инвентаре. */
    private void startDeliveries(Player player) {
        while (currentBatch.currentTarget() != null) {
            if (hasItemsToDeposit(player, currentBatch.currentItemIds())) break;
            debugLog("startDeliveries: skip " + currentBatch.currentTarget().toShortString()
                    + " (no items in inv)");
            currentBatch.advanceDelivery();
        }
        if (currentBatch.currentTarget() == null) {
            debugLog("startDeliveries: no deliveries remain, advancing batch");
            advanceBatch();
            return;
        }
        stuckResets = 0;
        debugLog("startDeliveries: -> " + currentBatch.currentTarget().toShortString());
        phase = Phase.WORK_NAV_DST;
    }

    private boolean hasItemsToDeposit(Player player, List<String> itemIds) {
        Set<String> idSet = new HashSet<>(itemIds);
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && idSet.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()))
                return true;
        }
        return false;
    }

    private void tickWorkNavDst(Player player, Level level) {
        BlockPos target = currentBatch.currentTarget();
        if (target == null) { advanceBatch(); return; }
        if (stuckResets >= MAX_STUCK_RESETS) {
            LOGGER.warn("[BetterTwink] Cannot reach dest chest {}, skipping delivery",
                    target.toShortString());
            stuckResets = 0;
            currentBatch.advanceDelivery();
            startDeliveries(player);
            return;
        }
        if (navigateTo(player, level, target)) {
            openRetries = 0;
            requestOpenContainer(target);
            waitTicks = 7;
            phase = Phase.WORK_OPEN_DST;
        }
    }

    private void tickWorkOpenDst(Player player) {
        BlockPos target = currentBatch.currentTarget();
        if (target == null) { advanceBatch(); return; }
        if (isContainerOpen(player)) {
            waitTicks = 4; // wait for ContainerSetContentPacket
            depositSlot = player.containerMenu.slots.size() - 36; // start at first player-inv slot
            phase = Phase.WORK_DEPOSIT;
        } else if (++openRetries < 3) {
            requestOpenContainer(target);
            waitTicks = 7;
        } else {
            debugLog("WORK_OPEN_DST: failed to open " + target.toShortString() + ", skipping delivery");
            currentBatch.advanceDelivery();
            startDeliveries(player);
        }
    }

    private void tickWorkDeposit(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!isContainerOpen(player)) {
            debugLog("WORK_DEPOSIT: container closed, advancing delivery");
            finishDelivery(player);
            return;
        }
        int total = menu.slots.size();
        Set<String> wantedIds = new HashSet<>(currentBatch.currentItemIds());

        while (depositSlot < total) {
            ItemStack s = menu.getSlot(depositSlot).getItem();
            if (!s.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                if (wantedIds.contains(id)) {
                    int slot = depositSlot++;
                    debugLog("WORK_DEPOSIT: slot " + slot + " = " + id
                            + " -> " + currentBatch.currentTarget().toShortString());
                    MC.gameMode.handleInventoryMouseClick(
                            menu.containerId, slot, 0, ClickType.QUICK_MOVE, MC.player);
                    waitTicks = getTransferTicks();
                    return;
                }
            }
            depositSlot++;
        }
        // Все слоты исчерпаны — переходим к уплотнению сундука
        compactSubState   = 0;
        compactPickedSlot = -1;
        compactTargetSlot = -1;
        compactIterations = 0;
        phase = Phase.WORK_COMPACT_DST;
    }

    // ===================== WORK: COMPACT DESTINATION CHEST ====

    /**
     * После депозита: уплотняем частичные стаки одинаковых предметов в сундуке.
     * Алгоритм за один тик:
     *   Состояние 0 (SCAN): найти первый предмет, у которого ≥2 слотов в сундуке.
     *                        Поднять стак из слота с наименьшим count → курсор.
     *   Состояние 1 (AFTER_PICKUP): курсор непустой → PICKUP_ALL (двойной клик).
     *   Состояние 2 (AFTER_PICKUP_ALL): положить курсор обратно в освобождённый слот.
     */
    private void tickWorkCompact(Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!isContainerOpen(player)) {
            debugLog("WORK_COMPACT: container closed, finishing delivery");
            finishDelivery(player);
            return;
        }
        int chestSlots = menu.slots.size() - 36;
        ItemStack carried = menu.getCarried();

        switch (compactSubState) {
            case 0: { // SCAN — find a pair to merge using exact item+NBT comparison
                if (compactIterations++ > 50) {
                    debugLog("WORK_COMPACT: max iterations reached, closing chest");
                    player.closeContainer();
                    waitTicks = 3;
                    finishDelivery(player);
                    return;
                }
                // Find a partial slot (i) that has another slot (j) with the IDENTICAL item+NBT
                int pickSlot = -1;
                int targetSlot = -1;
                outer:
                for (int i = 0; i < chestSlots; i++) {
                    ItemStack si = menu.getSlot(i).getItem();
                    if (si.isEmpty() || si.getCount() >= si.getMaxStackSize()) continue;
                    for (int j = 0; j < chestSlots; j++) {
                        if (j == i) continue;
                        ItemStack sj = menu.getSlot(j).getItem();
                        if (sj.isEmpty()) continue;
                        if (ItemStack.isSameItemSameTags(si, sj)) {
                            pickSlot  = i;
                            targetSlot = j;
                            break outer;
                        }
                    }
                }
                if (pickSlot == -1) {
                    // Nothing to compact — done
                    debugLog("WORK_COMPACT: done, closing chest");
                    player.closeContainer();
                    waitTicks = 3;
                    finishDelivery(player);
                    return;
                }
                compactPickedSlot = pickSlot;
                compactTargetSlot = targetSlot;
                debugLog("WORK_COMPACT: picking up slot " + pickSlot);
                MC.gameMode.handleInventoryMouseClick(
                        menu.containerId, pickSlot, 0, ClickType.PICKUP, MC.player);
                compactSubState = 1;
                waitTicks = getTransferTicks();
                break;
            }
            case 1: { // AFTER_PICKUP — do PICKUP_ALL on a non-empty slot with the same item
                if (carried.isEmpty()) {
                    // Pickup somehow failed, rescan
                    compactSubState = 0;
                    return;
                }
                // Find a non-empty slot that matches the carried item (compactTargetSlot may have moved)
                int allTarget = compactTargetSlot;
                if (allTarget < 0 || menu.getSlot(allTarget).getItem().isEmpty()) {
                    allTarget = -1;
                    for (int k = 0; k < chestSlots; k++) {
                        ItemStack sk = menu.getSlot(k).getItem();
                        if (!sk.isEmpty() && ItemStack.isSameItemSameTags(carried, sk)) {
                            allTarget = k;
                            break;
                        }
                    }
                }
                if (allTarget >= 0) {
                    MC.gameMode.handleInventoryMouseClick(
                            menu.containerId, allTarget, 0, ClickType.PICKUP_ALL, MC.player);
                }
                compactSubState = 2;
                waitTicks = getTransferTicks();
                break;
            }
            case 2: { // AFTER_PICKUP_ALL — place consolidated stack back
                if (carried.isEmpty()) {
                    compactSubState = 0;
                    return;
                }
                // Place back to the slot we picked from (it should be empty now)
                ItemStack slotNow = menu.getSlot(compactPickedSlot).getItem();
                int placeSlot = compactPickedSlot;
                if (!slotNow.isEmpty()) {
                    // Find first empty slot in chest
                    for (int i = 0; i < chestSlots; i++) {
                        if (menu.getSlot(i).getItem().isEmpty()) { placeSlot = i; break; }
                    }
                }
                MC.gameMode.handleInventoryMouseClick(
                        menu.containerId, placeSlot, 0, ClickType.PICKUP, MC.player);
                compactSubState = 0; // rescan
                waitTicks = getTransferTicks();
                break;
            }
        }
    }

    /** Common end-of-delivery logic (used by compact, deposit on container-close). */
    private void finishDelivery(Player player) {
        if (currentBatch.advanceDelivery()) {
            startDeliveries(player);
        } else {
            advanceBatch();
        }
    }

    private void advanceBatch() {
        stuckResets = 0;
        currentBatch = batchQueue.poll();
        if (currentBatch == null) {
            notify("\u00a7a[BetterTwink] \u00a7fПроход завершён. Запускаю повторное сканирование...");
            startScanPhase();
        } else {
            phase = Phase.WORK_NAV_SRC;
        }
    }

    // ===================== НАВИГАЦИЯ ===========================

    /**
     * Перемещает игрока к цели по A* пути.
     * Возвращает true, когда игрок достаточно близко для взаимодействия.
     */
    private boolean navigateTo(Player player, Level level, BlockPos target) {
        Vec3 pVec = player.position();
        Vec3 tVec = Vec3.atCenterOf(target);

        // Достаточно близко — взаимодействуем
        if (pVec.distanceTo(tVec) < REACH_DIST) {
            navActive = false;
            navDesiredJump = false;
            currentPath = null;
            lastNavTarget = null;
            return true;
        }

        // Пересчитать путь если цель изменилась или пути нет
        boolean needNewPath = currentPath == null || currentPath.isEmpty()
                || !target.equals(lastNavTarget);
        if (needNewPath) {
            // New target → reset stuck counters
            if (lastNavTarget != null && !lastNavTarget.equals(target)) {
                stuckResets = 0;
            }
            lastNavTarget = target;
            stuckCounter  = 0;
            lastPlayerPos = pVec;
            currentPath   = PathFinder.getInstance().findPath(
                    level, player.blockPosition(), target, player);
            pathNodeIndex = 1; // пропускаем стартовый узел
            LOGGER.debug("[BetterTwink] Path to {}: {} nodes", target.toShortString(),
                    currentPath.size());
        }

        // Обнаружение застревания (нет движения 25 тиков)
        if (lastPlayerPos != null && pVec.distanceTo(lastPlayerPos) < 0.05) {
            if (++stuckCounter > 25) {
                stuckCounter  = 0;
                stuckResets++;
                currentPath   = null; // пересчитать
                lastNavTarget = null;
                LOGGER.debug("[BetterTwink] Stuck — recomputing path (reset #{})", stuckResets);
                return false;
            }
        } else {
            stuckCounter  = 0;
            lastPlayerPos = pVec;
        }

        // Путь не найден — пробуем двигаться напрямую как fallback
        if (currentPath.isEmpty()) {
            moveDirectly(player, level, tVec);
            return false;
        }

        // Следуем по узлам пути
        while (pathNodeIndex < currentPath.size()) {
            BlockPos node = currentPath.get(pathNodeIndex);
            Vec3 nodeCenter = Vec3.atCenterOf(node);

            double horizDist = Math.sqrt(
                    Math.pow(nodeCenter.x - pVec.x, 2) +
                    Math.pow(nodeCenter.z - pVec.z, 2));

            boolean correctHeight = Math.abs(pVec.y - nodeCenter.y) < 1.2;

            if (horizDist < NODE_RADIUS && correctHeight) {
                pathNodeIndex++;   // узел достигнут — идём к следующему
            } else {
                // Двигаемся к текущему узлу
                moveToward(player, level, nodeCenter, node);
                return false;
            }
        }

        // Закончили путь, но ещё не достаточно близко — двигаемся напрямую
        currentPath = null;
        return false;
    }

    /**
     * Двигаемся к следующему узлу пути.
     * НЕ вызываем setDeltaMovement — вместо этого ставим navDesiredYaw/navActive,
     * которые BotTickHandler читает в Phase.START и инжектирует через KeyMapping.
     * Minecraft physics сам реализует коллизионно-корректное движение.
     */
    private void moveToward(Player player, Level level, Vec3 nodeCenter, BlockPos nodePos) {
        Vec3 pVec = player.position();
        double dx = nodeCenter.x - pVec.x;
        double dz = nodeCenter.z - pVec.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        boolean nodeAbove = nodePos.getY() > player.blockPosition().getY();

        if (horizDist > 0.05) {
            navDesiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        navDesiredXRot = nodeAbove ? -25f : 5f;
        navDesiredJump = nodeAbove;
        navActive = true;

        // Открываем двери на пути
        tryOpenDoor(level, nodePos);
        tryOpenDoor(level, nodePos.above());
    }

    /** Fallback: если A* не нашёл путь — идём напрямую (используя те же ключи). */
    private void moveDirectly(Player player, Level level, Vec3 target) {
        Vec3 pVec = player.position();
        double dx = target.x - pVec.x;
        double dz = target.z - pVec.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.1) return;

        navDesiredYaw  = (float) Math.toDegrees(Math.atan2(-dx, dz));
        navDesiredXRot = 0f;
        // Прыгаем только если реально упёрлись в стену
        navDesiredJump = player.horizontalCollision;
        navActive = true;

        Vec3 ahead = pVec.add((dx / dist) * 1.5, 0, (dz / dist) * 1.5);
        BlockPos aheadPos = BlockPos.containing(ahead);
        tryOpenDoor(level, aheadPos);
        tryOpenDoor(level, aheadPos.above());
    }

    private void tryOpenDoor(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN)
                && !state.getValue(BlockStateProperties.OPEN)) {
            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        }
    }

    // ===================== ТТЫ =============================

    /** Открыть контейнер (правый клик по блоку) */
    private void requestOpenContainer(BlockPos pos) {
        if (MC.player == null) return;
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eye    = MC.player.getEyePosition();
        Vec3 diff   = center.subtract(eye);
        double h    = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        MC.player.setYRot((float) Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        MC.player.setXRot((float) -Math.toDegrees(Math.atan2(diff.y, h)));
        // Use the face that the player is actually approaching from, not hardcoded UP
        Direction face = computeApproachFace(pos);
        MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(center, face, pos, false));
    }

    /** Finds which face of {@code pos} is closest to (i.e. being approached by) the player. */
    private Direction computeApproachFace(BlockPos pos) {
        if (MC.player == null) return Direction.NORTH;
        Vec3 p  = MC.player.position();
        double dx = p.x - (pos.getX() + 0.5);
        double dy = p.y - (pos.getY() + 0.5);
        double dz = p.z - (pos.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (az >= ax && az >= ay) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return dy > 0 ? Direction.UP : Direction.DOWN;
    }

    private boolean isContainerOpen(Player player) {
        return player.containerMenu != player.inventoryMenu;
    }

    /** адержка между перемещениями предметов в тиках (50ms = 1 тик) */
    private int getTransferTicks() {
        int ms    = BetterTwinkConfig.ITEM_TRANSFER_DELAY.get();
        int ticks = Math.max(1, ms / 50);
        ticks    += (int) (Math.random() * 2); // человеческая случайность
        return ticks;
    }

    private void notify(String msg) {
        if (MC.player != null)
            MC.player.displayClientMessage(Component.literal(msg), true);
    }

    // ===================== NAV INPUT GETTERS (читаются BotTickHandler @ Phase.START) =====
    public float   getNavDesiredYaw()  { return navDesiredYaw; }
    public float   getNavDesiredXRot() { return navDesiredXRot; }
    public boolean isNavJump()         { return navDesiredJump; }
    public boolean isNavActive()       { return navActive && isRunning; }

    // ===================== LEGACY COMPAT =======================
    public void startSorting() { startBot(); }
    public void stopSorting()  { stopBot(); }
    public boolean isActive()  { return isRunning; }
    public BlockPos getCurrentTarget2() { return getCurrentTarget(); }
}