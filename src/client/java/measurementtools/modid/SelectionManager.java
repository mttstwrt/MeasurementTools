package measurementtools.modid;

import measurementtools.modid.shapes.EllipsoidMode;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();

    private final List<BlockPos> selectedBlocks = new ArrayList<>();
    private ShapeMode currentShapeMode = ShapeMode.RECTANGLE;
    private EllipsoidMode ellipsoidMode = EllipsoidMode.FIT_TO_BOX;
    private int subdivisionCount = 0;
    private int splineRadius = 0;
    private boolean blockCountingEnabled = false;
    private boolean hollowMode = false;
    private boolean chunkBoundariesEnabled = false;

    // Layer mode state
    private boolean layerModeEnabled = false;
    private int currentLayer = 0; // Relative to minY

    // Cached bounds (invalidated when selection changes)
    private BlockPos cachedMinPos = null;
    private BlockPos cachedMaxPos = null;
    private boolean boundsCacheDirty = true;

    private SelectionManager() {}

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public void addBlock(BlockPos pos) {
        if (pos != null && !selectedBlocks.contains(pos)) {
            UndoRedoManager.getInstance().saveState();
            selectedBlocks.add(pos);
            invalidateBoundsCache();
        }
    }

    public void clearSelection() {
        if (!selectedBlocks.isEmpty()) {
            UndoRedoManager.getInstance().saveState();
        }
        selectedBlocks.clear();
        invalidateBoundsCache();
    }

    /**
     * Sets the selected blocks list directly (used for undo/redo).
     * Does not record history.
     */
    public void setSelectedBlocks(List<BlockPos> blocks) {
        selectedBlocks.clear();
        selectedBlocks.addAll(blocks);
        invalidateBoundsCache();
    }

    private void invalidateBoundsCache() {
        boundsCacheDirty = true;
        cachedMinPos = null;
        cachedMaxPos = null;
    }

    private void updateBoundsCache() {
        if (!boundsCacheDirty) return;

        if (selectedBlocks.isEmpty()) {
            cachedMinPos = null;
            cachedMaxPos = null;
        } else {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (BlockPos pos : selectedBlocks) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            cachedMinPos = new BlockPos(minX, minY, minZ);
            cachedMaxPos = new BlockPos(maxX, maxY, maxZ);
        }
        boundsCacheDirty = false;
    }

    public List<BlockPos> getSelectedBlocks() {
        return Collections.unmodifiableList(selectedBlocks);
    }

    public boolean hasSelection() {
        return !selectedBlocks.isEmpty();
    }

    public BlockPos getMinPos() {
        updateBoundsCache();
        return cachedMinPos;
    }

    public BlockPos getMaxPos() {
        updateBoundsCache();
        return cachedMaxPos;
    }

    public void setShapeMode(ShapeMode mode) {
        this.currentShapeMode = mode;
    }

    public ShapeMode getShapeMode() {
        return currentShapeMode;
    }

    public void setSubdivisionCount(int count) {
        this.subdivisionCount = Math.max(0, count);
    }

    public int getSubdivisionCount() {
        return subdivisionCount;
    }

    private static final int[] SUBDIVISION_OPTIONS = {0, 2, 3, 4, 5, 8, 10, 16};

    public void cycleSubdivision() {
        int currentIndex = getSubdivisionIndex();
        subdivisionCount = SUBDIVISION_OPTIONS[(currentIndex + 1) % SUBDIVISION_OPTIONS.length];
    }

    public void stepSubdivisionUp() {
        int currentIndex = getSubdivisionIndex();
        if (currentIndex < SUBDIVISION_OPTIONS.length - 1) {
            subdivisionCount = SUBDIVISION_OPTIONS[currentIndex + 1];
        }
    }

    public void stepSubdivisionDown() {
        int currentIndex = getSubdivisionIndex();
        if (currentIndex > 0) {
            subdivisionCount = SUBDIVISION_OPTIONS[currentIndex - 1];
        }
    }

    private int getSubdivisionIndex() {
        for (int i = 0; i < SUBDIVISION_OPTIONS.length; i++) {
            if (SUBDIVISION_OPTIONS[i] == subdivisionCount) {
                return i;
            }
        }
        return 0;
    }

    // Spline radius options (0 = line only, 1+ = tube radius in blocks)
    private static final int MAX_SPLINE_RADIUS = 16;

    public int getSplineRadius() {
        return splineRadius;
    }

    public void setSplineRadius(int radius) {
        this.splineRadius = Math.max(0, Math.min(MAX_SPLINE_RADIUS, radius));
    }

    public void stepSplineRadiusUp() {
        if (splineRadius < MAX_SPLINE_RADIUS) {
            splineRadius++;
        }
    }

    public void stepSplineRadiusDown() {
        if (splineRadius > 0) {
            splineRadius--;
        }
    }

    public BlockPos getCenterBlock() {
        return selectedBlocks.isEmpty() ? null : selectedBlocks.get(0);
    }

    public double getMaxRadiusXZ() {
        if (selectedBlocks.size() < 2) return 0;
        BlockPos center = getCenterBlock();
        double maxRadius = 0;
        for (int i = 1; i < selectedBlocks.size(); i++) {
            BlockPos pos = selectedBlocks.get(i);
            double dx = pos.getX() - center.getX();
            double dz = pos.getZ() - center.getZ();
            double radius = Math.sqrt(dx * dx + dz * dz);
            if (radius > maxRadius) {
                maxRadius = radius;
            }
        }
        return maxRadius;
    }

    public int getMinY() {
        BlockPos min = getMinPos();
        return min != null ? min.getY() : 0;
    }

    public int getMaxY() {
        BlockPos max = getMaxPos();
        return max != null ? max.getY() : 0;
    }

    public boolean isBlockCountingEnabled() {
        return blockCountingEnabled;
    }

    public void setBlockCountingEnabled(boolean enabled) {
        this.blockCountingEnabled = enabled;
    }

    public void toggleBlockCounting() {
        this.blockCountingEnabled = !this.blockCountingEnabled;
    }

    public EllipsoidMode getEllipsoidMode() {
        return ellipsoidMode;
    }

    public void setEllipsoidMode(EllipsoidMode mode) {
        this.ellipsoidMode = mode;
    }

    public boolean isHollowMode() {
        return hollowMode;
    }

    public void setHollowMode(boolean hollowMode) {
        this.hollowMode = hollowMode;
    }

    public void toggleHollowMode() {
        this.hollowMode = !this.hollowMode;
    }

    public boolean isChunkBoundariesEnabled() {
        return chunkBoundariesEnabled;
    }

    public void setChunkBoundariesEnabled(boolean enabled) {
        this.chunkBoundariesEnabled = enabled;
    }

    public void toggleChunkBoundaries() {
        this.chunkBoundariesEnabled = !this.chunkBoundariesEnabled;
    }

    public boolean isLayerModeEnabled() {
        return layerModeEnabled;
    }

    public void setLayerModeEnabled(boolean enabled) {
        this.layerModeEnabled = enabled;
        if (enabled) {
            // Reset layer to middle when enabling
            int height = getMaxY() - getMinY();
            currentLayer = height / 2;
        }
    }

    public void toggleLayerMode() {
        setLayerModeEnabled(!layerModeEnabled);
    }

    public int getCurrentLayer() {
        return currentLayer;
    }

    public void setCurrentLayer(int layer) {
        this.currentLayer = layer;
    }

    public void cycleLayerUp() {
        if (!hasSelection()) return;
        int maxHeight = getMaxY() - getMinY();
        if (currentLayer < maxHeight) {
            currentLayer++;
        }
    }

    public void cycleLayerDown() {
        if (currentLayer > 0) {
            currentLayer--;
        }
    }

    /**
     * Gets the absolute Y coordinate of the current layer.
     */
    public int getCurrentLayerY() {
        return getMinY() + currentLayer;
    }

    /**
     * Gets the total number of layers in the current selection.
     */
    public int getLayerCount() {
        if (!hasSelection()) return 0;
        return getMaxY() - getMinY() + 1;
    }
}
