package measurementtools.modid.render;

import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.EllipsoidMode;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Calculates which blocks form the surface of various shapes.
 * Used by hollow mode to render individual block outlines.
 */
public class HollowBlockCalculator {

    /**
     * Gets the blocks that form the surface of the current shape.
     * @param filterLayer if not -1, only returns blocks at this absolute Y level
     */
    public static Set<BlockPos> calculateHollowBlocks(ShapeMode mode, int filterLayer) {
        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) {
            return Set.of();
        }

        return switch (mode) {
            case RECTANGLE -> calculateRectangleHollow(manager, filterLayer);
            case CYLINDER -> calculateCylinderHollow(manager, filterLayer);
            case ELLIPSOID -> calculateEllipsoidHollow(manager, filterLayer);
            case SPLINE -> Set.of(); // Spline is a curve, hollow mode not applicable
        };
    }

    /**
     * Calculates blocks forming the shell of a rectangular box (6 faces).
     */
    private static Set<BlockPos> calculateRectangleHollow(SelectionManager manager, int filterLayer) {
        Set<BlockPos> blocks = new HashSet<>();

        BlockPos minPos = manager.getMinPos();
        BlockPos maxPos = manager.getMaxPos();
        if (minPos == null || maxPos == null) return blocks;

        int minX = minPos.getX();
        int minY = minPos.getY();
        int minZ = minPos.getZ();
        int maxX = maxPos.getX();
        int maxY = maxPos.getY();
        int maxZ = maxPos.getZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (filterLayer != -1 && y != filterLayer) continue;

                for (int z = minZ; z <= maxZ; z++) {
                    // Block is on surface if it's on any face of the box
                    boolean onSurface = (x == minX || x == maxX ||
                                        y == minY || y == maxY ||
                                        z == minZ || z == maxZ);
                    if (onSurface) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Calculates blocks forming the shell of a cylinder.
     * Uses the Midpoint Circle Algorithm for the circular cross-section.
     */
    private static Set<BlockPos> calculateCylinderHollow(SelectionManager manager, int filterLayer) {
        Set<BlockPos> blocks = new HashSet<>();

        BlockPos center = manager.getCenterBlock();
        if (center == null) return blocks;

        double radius = manager.getMaxRadiusXZ();
        if (radius < 0.5) radius = 0.5;

        int minY = manager.getMinY();
        int maxY = manager.getMaxY();
        int centerX = center.getX();
        int centerZ = center.getZ();
        int intRadius = (int) Math.ceil(radius);

        for (int y = minY; y <= maxY; y++) {
            if (filterLayer != -1 && y != filterLayer) continue;

            boolean isCapLayer = (y == minY || y == maxY);

            // For each block in the potential radius range
            for (int dx = -intRadius; dx <= intRadius; dx++) {
                for (int dz = -intRadius; dz <= intRadius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    if (isCapLayer) {
                        // Cap: include all blocks inside the circle
                        if (dist <= radius + 0.5) {
                            blocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                        }
                    } else {
                        // Side: only include blocks on the circumference
                        // A block is on the circumference if its center is within 0.5 of the radius
                        if (dist >= radius - 0.5 && dist <= radius + 0.5) {
                            blocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                        }
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Calculates blocks forming the shell of an ellipsoid.
     * Uses the ellipsoid equation: (x/rx)^2 + (y/ry)^2 + (z/rz)^2 = 1
     */
    private static Set<BlockPos> calculateEllipsoidHollow(SelectionManager manager, int filterLayer) {
        Set<BlockPos> blocks = new HashSet<>();

        double centerX, centerY, centerZ;
        double radiusX, radiusY, radiusZ;

        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            BlockPos minPos = manager.getMinPos();
            BlockPos maxPos = manager.getMaxPos();
            if (minPos == null || maxPos == null) return blocks;

            centerX = (minPos.getX() + maxPos.getX() + 1) / 2.0;
            centerY = (minPos.getY() + maxPos.getY() + 1) / 2.0;
            centerZ = (minPos.getZ() + maxPos.getZ() + 1) / 2.0;

            radiusX = (maxPos.getX() - minPos.getX() + 1) / 2.0;
            radiusY = (maxPos.getY() - minPos.getY() + 1) / 2.0;
            radiusZ = (maxPos.getZ() - minPos.getZ() + 1) / 2.0;
        } else {
            BlockPos center = manager.getCenterBlock();
            if (center == null) return blocks;

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

        // Iterate over all blocks that could possibly be on the ellipsoid surface
        int maxRadX = (int) Math.ceil(radiusX) + 1;
        int maxRadY = (int) Math.ceil(radiusY) + 1;
        int maxRadZ = (int) Math.ceil(radiusZ) + 1;

        for (int y = -maxRadY; y <= maxRadY; y++) {
            int worldY = (int) Math.floor(centerY) + y;
            if (filterLayer != -1 && worldY != filterLayer) continue;

            for (int x = -maxRadX; x <= maxRadX; x++) {
                for (int z = -maxRadZ; z <= maxRadZ; z++) {
                    int worldX = (int) Math.floor(centerX) + x;
                    int worldZ = (int) Math.floor(centerZ) + z;

                    // Check if this block's center is on the ellipsoid surface
                    double blockCenterX = worldX + 0.5;
                    double blockCenterY = worldY + 0.5;
                    double blockCenterZ = worldZ + 0.5;

                    double nx = (blockCenterX - centerX) / radiusX;
                    double ny = (blockCenterY - centerY) / radiusY;
                    double nz = (blockCenterZ - centerZ) / radiusZ;
                    double dist = nx * nx + ny * ny + nz * nz;

                    // Block is on surface if normalized distance is close to 1
                    // Use a threshold that accounts for block size relative to radius
                    double threshold = 0.5 / Math.min(Math.min(radiusX, radiusY), radiusZ);
                    threshold = Math.max(0.15, Math.min(0.5, threshold));

                    if (Math.abs(dist - 1.0) <= threshold) {
                        blocks.add(new BlockPos(worldX, worldY, worldZ));
                    }
                }
            }
        }

        return blocks;
    }
}
