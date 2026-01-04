package measurementtools.modid;

import measurementtools.modid.shapes.EllipsoidMode;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class BlockCounter {
    private static final BlockCounter INSTANCE = new BlockCounter();

    private Map<Block, Integer> cachedCounts = new HashMap<>();
    private int cachedTotalBlocks = 0;
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION_MS = 100;

    private BlockCounter() {}

    public static BlockCounter getInstance() {
        return INSTANCE;
    }

    public Map<Block, Integer> getBlockCounts() {
        updateCacheIfNeeded();
        return cachedCounts;
    }

    public int getTotalBlockCount() {
        updateCacheIfNeeded();
        return cachedTotalBlocks;
    }

    private void updateCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < CACHE_DURATION_MS) {
            return;
        }
        lastUpdateTime = now;

        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) {
            cachedCounts = new HashMap<>();
            cachedTotalBlocks = 0;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            cachedCounts = new HashMap<>();
            cachedTotalBlocks = 0;
            return;
        }

        Map<Block, Integer> counts = countBlocksInSelection(client.world, manager);

        // Sort by count descending
        cachedCounts = counts.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        cachedTotalBlocks = counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private Map<Block, Integer> countBlocksInSelection(World world, SelectionManager manager) {
        Map<Block, Integer> counts = new HashMap<>();
        ShapeMode mode = manager.getShapeMode();

        switch (mode) {
            case RECTANGLE -> countRectangle(world, manager, counts);
            case CYLINDER -> countCylinder(world, manager, counts);
            case ELLIPSOID -> countEllipsoid(world, manager, counts);
        }

        return counts;
    }

    private void countRectangle(World world, SelectionManager manager, Map<Block, Integer> counts) {
        BlockPos minPos = manager.getMinPos();
        BlockPos maxPos = manager.getMaxPos();
        if (minPos == null || maxPos == null) return;

        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    addBlockToCount(world, pos, counts);
                }
            }
        }
    }

    private void countCylinder(World world, SelectionManager manager, Map<Block, Integer> counts) {
        BlockPos center = manager.getCenterBlock();
        if (center == null) return;

        double radius = manager.getMaxRadiusXZ();
        if (radius < 0.5) radius = 0.5;

        int minY = manager.getMinY();
        int maxY = manager.getMaxY();

        double centerX = center.getX() + 0.5;
        double centerZ = center.getZ() + 0.5;

        // Calculate bounding box for iteration
        int minX = (int) Math.floor(centerX - radius - 1);
        int maxX = (int) Math.ceil(centerX + radius + 1);
        int minZ = (int) Math.floor(centerZ - radius - 1);
        int maxZ = (int) Math.ceil(centerZ + radius + 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double blockCenterX = x + 0.5;
                    double blockCenterZ = z + 0.5;

                    double dx = blockCenterX - centerX;
                    double dz = blockCenterZ - centerZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);

                    if (distance <= radius + 0.5) {
                        BlockPos pos = new BlockPos(x, y, z);
                        addBlockToCount(world, pos, counts);
                    }
                }
            }
        }
    }

    private void countEllipsoid(World world, SelectionManager manager, Map<Block, Integer> counts) {
        double centerX, centerY, centerZ;
        double radiusX, radiusY, radiusZ;

        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            // Fit ellipsoid inside bounding box
            BlockPos minPos = manager.getMinPos();
            BlockPos maxPos = manager.getMaxPos();
            if (minPos == null || maxPos == null) return;

            centerX = (minPos.getX() + maxPos.getX() + 1) / 2.0;
            centerY = (minPos.getY() + maxPos.getY() + 1) / 2.0;
            centerZ = (minPos.getZ() + maxPos.getZ() + 1) / 2.0;

            radiusX = (maxPos.getX() - minPos.getX() + 1) / 2.0;
            radiusY = (maxPos.getY() - minPos.getY() + 1) / 2.0;
            radiusZ = (maxPos.getZ() - minPos.getZ() + 1) / 2.0;
        } else {
            // CENTER_RADIUS mode: first block is center, furthest defines XZ radius
            BlockPos center = manager.getCenterBlock();
            if (center == null) return;

            centerX = center.getX() + 0.5;
            centerZ = center.getZ() + 0.5;

            double radiusXZ = manager.getMaxRadiusXZ();
            if (radiusXZ < 0.5) radiusXZ = 0.5;
            radiusX = radiusXZ;
            radiusZ = radiusXZ;

            int minY = manager.getMinY();
            int maxY = manager.getMaxY();
            radiusY = (maxY - minY + 1) / 2.0;
            centerY = (minY + maxY + 1) / 2.0;
        }

        if (radiusX < 0.5) radiusX = 0.5;
        if (radiusY < 0.5) radiusY = 0.5;
        if (radiusZ < 0.5) radiusZ = 0.5;

        // Calculate bounding box for iteration
        int iterMinX = (int) Math.floor(centerX - radiusX - 1);
        int iterMaxX = (int) Math.ceil(centerX + radiusX + 1);
        int iterMinY = (int) Math.floor(centerY - radiusY - 1);
        int iterMaxY = (int) Math.ceil(centerY + radiusY + 1);
        int iterMinZ = (int) Math.floor(centerZ - radiusZ - 1);
        int iterMaxZ = (int) Math.ceil(centerZ + radiusZ + 1);

        for (int x = iterMinX; x <= iterMaxX; x++) {
            for (int y = iterMinY; y <= iterMaxY; y++) {
                for (int z = iterMinZ; z <= iterMaxZ; z++) {
                    double blockCenterX = x + 0.5;
                    double blockCenterY = y + 0.5;
                    double blockCenterZ = z + 0.5;

                    double dx = (blockCenterX - centerX) / radiusX;
                    double dy = (blockCenterY - centerY) / radiusY;
                    double dz = (blockCenterZ - centerZ) / radiusZ;

                    double ellipsoidValue = dx * dx + dy * dy + dz * dz;

                    double minRadius = Math.min(Math.min(radiusX, radiusY), radiusZ);
                    if (ellipsoidValue <= 1.0 + 0.5 / minRadius) {
                        BlockPos pos = new BlockPos(x, y, z);
                        addBlockToCount(world, pos, counts);
                    }
                }
            }
        }
    }

    private void addBlockToCount(World world, BlockPos pos, Map<Block, Integer> counts) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;

        Block block = state.getBlock();
        counts.merge(block, 1, Integer::sum);
    }

    public void invalidateCache() {
        lastUpdateTime = 0;
    }
}
