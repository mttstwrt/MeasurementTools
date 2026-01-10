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
            case SPLINE -> calculateSplineHollow(manager, filterLayer);
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

    /**
     * Calculates all blocks that a spline curve passes through.
     * Uses Catmull-Rom interpolation and samples many points along the curve.
     */
    private static Set<BlockPos> calculateSplineHollow(SelectionManager manager, int filterLayer) {
        Set<BlockPos> blocks = new HashSet<>();

        java.util.List<BlockPos> selection = manager.getSelectedBlocks();
        if (selection.size() < 2) return blocks;

        // Convert to double arrays for easier math
        double[][] points = new double[selection.size()][3];
        for (int i = 0; i < selection.size(); i++) {
            BlockPos pos = selection.get(i);
            points[i][0] = pos.getX() + 0.5;
            points[i][1] = pos.getY() + 0.5;
            points[i][2] = pos.getZ() + 0.5;
        }

        // Sample points along the spline and collect block positions
        int n = points.length;
        int samplesPerSegment = 32; // Higher = more accurate block detection

        for (int i = 0; i < n - 1; i++) {
            // Get the 4 control points for this segment
            double[] p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
            double[] p1 = points[i];
            double[] p2 = points[i + 1];
            double[] p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            // Sample along this segment
            for (int seg = 0; seg <= samplesPerSegment; seg++) {
                double t = (double) seg / samplesPerSegment;
                double[] point = catmullRom(p0, p1, p2, p3, t);

                int blockX = (int) Math.floor(point[0]);
                int blockY = (int) Math.floor(point[1]);
                int blockZ = (int) Math.floor(point[2]);

                if (filterLayer == -1 || blockY == filterLayer) {
                    blocks.add(new BlockPos(blockX, blockY, blockZ));
                }
            }
        }

        return blocks;
    }

    /**
     * Catmull-Rom spline interpolation.
     */
    private static double[] catmullRom(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = 0.5 * ((2 * p1[i]) +
                              (-p0[i] + p2[i]) * t +
                              (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                              (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3);
        }
        return result;
    }

    /**
     * Extrapolates a phantom point before the start of the curve.
     */
    private static double[] extrapolateStart(double[] p0, double[] p1) {
        return new double[] {
            2 * p0[0] - p1[0],
            2 * p0[1] - p1[1],
            2 * p0[2] - p1[2]
        };
    }

    /**
     * Extrapolates a phantom point after the end of the curve.
     */
    private static double[] extrapolateEnd(double[] pn1, double[] pn) {
        return new double[] {
            2 * pn[0] - pn1[0],
            2 * pn[1] - pn1[1],
            2 * pn[2] - pn1[2]
        };
    }
}
