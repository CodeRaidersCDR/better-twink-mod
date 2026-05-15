package com.minemods.bettertwink.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/**
 * Pathfinder A* for Minecraft bot navigation.
 *
 * Node = BlockPos where player FEET are (same as player.blockPosition()).
 * A position is standable when:
 *   - floor block (pos.below()) has motion-blocking properties
 *   - feet block (pos) is passable
 *   - head block (pos.above()) is passable
 *
 * Neighbours: 4 cardinal horizontal directions with optional +1/-1/-2 Y step.
 */
public class PathFinder {

    private static PathFinder INSTANCE;

    /** Max nodes explored before giving up */
    private static final int MAX_OPEN_SIZE = 3000;
    /** Max nodes in the returned path */
    private static final int MAX_PATH_NODES = 300;

    private PathFinder() {}

    public static PathFinder getInstance() {
        if (INSTANCE == null) INSTANCE = new PathFinder();
        return INSTANCE;
    }

    // ──────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────

    /**
     * Find a walkable path from {@code start} to reach {@code target}.
     * If target is solid (e.g. a chest), finds nearest standable neighbour.
     * Returns empty list if unreachable.
     */
    public List<BlockPos> findPath(Level level, BlockPos start, BlockPos end, Player player) {
        // Target might be solid — find a standable spot near it
        BlockPos goal = isStandable(level, end) ? end : findNearestStandable(level, end, start);
        if (goal == null) return Collections.emptyList();
        if (start.equals(goal) || start.closerThan(goal, 1.5)) return List.of(start, goal);

        // A* ──────────────────────────────────────────────────
        // gScore: best known cost from start to node
        Map<BlockPos, Double> gScore = new HashMap<>();
        // parent map for path reconstruction
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();

        // Open set sorted by f = g + h
        final BlockPos goalFinal = goal;
        PriorityQueue<BlockPos> open = new PriorityQueue<>(
                Comparator.comparingDouble(n ->
                        gScore.getOrDefault(n, Double.MAX_VALUE) + heuristic(n, goalFinal)));

        Set<BlockPos> closed = new HashSet<>();

        gScore.put(start, 0.0);
        open.add(start);

        while (!open.isEmpty() && gScore.size() < MAX_OPEN_SIZE) {
            BlockPos current = open.poll();

            // Close enough to goal — reconstruct
            if (current.closerThan(goal, 1.6)) {
                return reconstructPath(cameFrom, current);
            }

            if (closed.contains(current)) continue;
            closed.add(current);

            for (Neighbour nb : getNeighbours(level, current)) {
                if (closed.contains(nb.pos)) continue;

                double tentative = gScore.getOrDefault(current, Double.MAX_VALUE) + nb.cost;
                if (tentative < gScore.getOrDefault(nb.pos, Double.MAX_VALUE)) {
                    gScore.put(nb.pos, tentative);
                    cameFrom.put(nb.pos, current);
                    // Remove and re-add so priority queue reorders
                    open.remove(nb.pos);
                    open.add(nb.pos);
                }
            }
        }

        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────────────────
    //  Neighbour generation
    // ──────────────────────────────────────────────────────────

    private List<Neighbour> getNeighbours(Level level, BlockPos pos) {
        List<Neighbour> result = new ArrayList<>(8);

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos flat     = pos.relative(dir);
            BlockPos stepUp   = flat.above();
            BlockPos stepDown = flat.below();
            BlockPos fall2    = flat.below(2);

            // ── Flat walk (same Y) ──────────────────────────────
            if (isStandable(level, flat)) {
                result.add(new Neighbour(flat, 1.0 + mobCost(level, flat)));
            }

            // ── Step up 1 block (stair / raised floor) ──────────
            // Need head-room at current position AND standable at stepUp
            if (isPassable(level, pos.above()) && isStandable(level, stepUp)) {
                result.add(new Neighbour(stepUp, 1.2 + mobCost(level, stepUp)));
            }

            // ── Step down 1 block ────────────────────────────────
            // flat itself must be passable (we walk through it while descending)
            if (isPassable(level, flat) && isStandable(level, stepDown)) {
                result.add(new Neighbour(stepDown, 1.2 + mobCost(level, stepDown)));
            }

            // ── Fall 2 blocks (ledge drop) ───────────────────────
            if (isPassable(level, flat) && isPassable(level, flat.below())
                    && isStandable(level, fall2)) {
                result.add(new Neighbour(fall2, 1.8 + mobCost(level, fall2)));
            }
        }

        return result;
    }

    /**
     * Returns additional A* cost (+3.0) if a hostile mob occupies {@code pos}.
     * Makes the pathfinder naturally route around mobs rather than through them.
     */
    private double mobCost(Level level, BlockPos pos) {
        AABB box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos)).inflate(0.1);
        return level.getEntitiesOfClass(Mob.class, box).isEmpty() ? 0.0 : 3.0;
    }

    // ──────────────────────────────────────────────────────────
    //  Walkability helpers
    // ──────────────────────────────────────────────────────────

    /**
     * Player can stand here: passable feet+head AND solid floor.
     */
    public boolean isStandable(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || level.isOutsideBuildHeight(pos.getY())) return false;
        return isPassable(level, pos)
                && isPassable(level, pos.above())
                && hasFloor(level, pos);
    }

    /**
     * Block is passable — player body can occupy it.
     * Uses collision shape so open doors (empty shape) are correctly passable
     * while closed doors (non-empty shape) correctly block the path.
     */
    public boolean isPassable(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || level.isOutsideBuildHeight(pos.getY())) return false;
        var state = level.getBlockState(pos);
        if (state.isAir()) return true;
        // Door/trapdoor: always treat as passable — tryOpenDoor() will open them during navigation
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        // getCollisionShape() returns Shapes.empty() for plants, open blocks, etc.
        return state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    /**
     * There is something to stand on below this position.
     */
    public boolean hasFloor(Level level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        if (below.isAir()) return false;
        return !below.getCollisionShape(level, pos.below(), CollisionContext.empty()).isEmpty();
    }

    // ──────────────────────────────────────────────────────────
    //  Utilities
    // ──────────────────────────────────────────────────────────

    /** Manhattan distance heuristic */
    private double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
             + Math.abs(a.getY() - b.getY())
             + Math.abs(a.getZ() - b.getZ());
    }

    /** Reconstruct path from cameFrom map, trimmed to MAX_PATH_NODES */
    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current) {
        Deque<BlockPos> deque = new ArrayDeque<>();
        deque.addFirst(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            deque.addFirst(current);
        }
        List<BlockPos> list = new ArrayList<>(deque);
        if (list.size() > MAX_PATH_NODES) list = list.subList(0, MAX_PATH_NODES);
        return list;
    }

    /**
     * Find nearest standable position adjacent to {@code target},
     * preferring positions closer to {@code from}.
     */
    private BlockPos findNearestStandable(Level level, BlockPos target, BlockPos from) {
        BlockPos best    = null;
        double bestDist  = Double.MAX_VALUE;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos candidate = target.relative(dir).above(dy);
                if (isStandable(level, candidate)) {
                    double d = heuristic(candidate, from);
                    if (d < bestDist) { bestDist = d; best = candidate; }
                }
            }
        }
        return best;
    }

    // ──────────────────────────────────────────────────────────
    //  Inner class
    // ──────────────────────────────────────────────────────────

    private record Neighbour(BlockPos pos, double cost) {}
}