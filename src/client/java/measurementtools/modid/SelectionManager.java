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
    private boolean blockCountingEnabled = false;
    private boolean hollowMode = false;

    // Layer mode state
    private boolean layerModeEnabled = false;
    private int currentLayer = 0; // Relative to minY

    private SelectionManager() {}

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public void addBlock(BlockPos pos) {
        if (pos != null && !selectedBlocks.contains(pos)) {
            selectedBlocks.add(pos);
        }
    }

    public void clearSelection() {
        selectedBlocks.clear();
    }

    public List<BlockPos> getSelectedBlocks() {
        return Collections.unmodifiableList(selectedBlocks);
    }

    public boolean hasSelection() {
        return !selectedBlocks.isEmpty();
    }

    public BlockPos getMinPos() {
        if (selectedBlocks.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : selectedBlocks) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
        }
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos getMaxPos() {
        if (selectedBlocks.isEmpty()) return null;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : selectedBlocks) {
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }
        return new BlockPos(maxX, maxY, maxZ);
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

    public void cycleSubdivision() {
        int[] options = {0, 2, 3, 4, 5, 8, 10, 16};
        int currentIndex = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == subdivisionCount) {
                currentIndex = i;
                break;
            }
        }
        subdivisionCount = options[(currentIndex + 1) % options.length];
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
        if (selectedBlocks.isEmpty()) return 0;
        int minY = Integer.MAX_VALUE;
        for (BlockPos pos : selectedBlocks) {
            if (pos.getY() < minY) minY = pos.getY();
        }
        return minY;
    }

    public int getMaxY() {
        if (selectedBlocks.isEmpty()) return 0;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : selectedBlocks) {
            if (pos.getY() > maxY) maxY = pos.getY();
        }
        return maxY;
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
