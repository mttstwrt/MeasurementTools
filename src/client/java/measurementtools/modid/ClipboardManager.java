package measurementtools.modid;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClipboardManager {
    private static final ClipboardManager INSTANCE = new ClipboardManager();

    // Clipboard storage: relative positions (from origin 0,0,0) with their BlockStates
    private final Map<BlockPos, BlockState> clipboardBlocks = new HashMap<>();

    // Paste preview state
    private boolean pastePreviewActive = false;
    private BlockPos previewAnchorPos = null;

    // Rotation state (0, 1, 2, 3 = 0°, 90°, 180°, 270° clockwise around Y axis)
    private int previewRotation = 0;

    // Cached rotated blocks (invalidated when rotation changes)
    private Map<BlockPos, BlockState> rotatedClipboardBlocks = null;
    private int cachedRotation = -1;

    // Clipboard dimensions for rotation pivot
    private int clipboardSizeX = 0;
    private int clipboardSizeZ = 0;

    // Locked placements: each entry is (anchorPos, relativeBlocks with states)
    private final List<LockedPlacement> lockedPlacements = new ArrayList<>();

    // Layer view for locked placements (after pasting)
    private boolean layerViewEnabled = false;
    private int currentViewLayer = 0; // Relative Y level being viewed

    private ClipboardManager() {}

    public static ClipboardManager getInstance() {
        return INSTANCE;
    }

    /**
     * Copies the current selection from SelectionManager into the clipboard.
     * Blocks are stored relative to the selection's minimum corner.
     */
    public void copySelection(World world) {
        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection() || world == null) {
            return;
        }

        clipboardBlocks.clear();
        rotatedClipboardBlocks = null;
        cachedRotation = -1;
        previewRotation = 0;

        BlockPos origin = manager.getMinPos();
        if (origin == null) return;

        for (BlockPos pos : manager.getSelectedBlocks()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                // Store relative to origin
                BlockPos relativePos = pos.subtract(origin);
                clipboardBlocks.put(relativePos, state);
            }
        }

        // Also capture all blocks within the bounding box (not just selected positions)
        BlockPos maxPos = manager.getMaxPos();
        if (maxPos != null) {
            // Store clipboard dimensions for rotation
            clipboardSizeX = maxPos.getX() - origin.getX();
            clipboardSizeZ = maxPos.getZ() - origin.getZ();

            for (int x = origin.getX(); x <= maxPos.getX(); x++) {
                for (int y = origin.getY(); y <= maxPos.getY(); y++) {
                    for (int z = origin.getZ(); z <= maxPos.getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (!state.isAir()) {
                            BlockPos relativePos = pos.subtract(origin);
                            clipboardBlocks.put(relativePos, state);
                        }
                    }
                }
            }
        }
    }

    public boolean hasClipboardData() {
        return !clipboardBlocks.isEmpty();
    }

    /**
     * Gets the clipboard blocks with current rotation applied.
     */
    public Map<BlockPos, BlockState> getClipboardBlocks() {
        if (previewRotation == 0) {
            return clipboardBlocks;
        }
        return getRotatedClipboardBlocks();
    }

    /**
     * Gets the raw (unrotated) clipboard blocks.
     */
    public Map<BlockPos, BlockState> getRawClipboardBlocks() {
        return clipboardBlocks;
    }

    /**
     * Gets the cached rotated clipboard blocks, computing if necessary.
     */
    private Map<BlockPos, BlockState> getRotatedClipboardBlocks() {
        if (rotatedClipboardBlocks == null || cachedRotation != previewRotation) {
            rotatedClipboardBlocks = computeRotatedBlocks(clipboardBlocks, previewRotation);
            cachedRotation = previewRotation;
        }
        return rotatedClipboardBlocks;
    }

    /**
     * Computes rotated block positions and states.
     * Rotation is clockwise around Y axis when viewed from above.
     */
    private Map<BlockPos, BlockState> computeRotatedBlocks(Map<BlockPos, BlockState> blocks, int rotation) {
        if (rotation == 0) {
            return new HashMap<>(blocks);
        }

        Map<BlockPos, BlockState> rotated = new HashMap<>();
        BlockRotation blockRotation = getBlockRotation(rotation);

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            // Rotate position around origin
            BlockPos rotatedPos = rotatePosition(pos, rotation);

            // Rotate block state (handles directional blocks like stairs, logs, etc.)
            BlockState rotatedState = state.rotate(blockRotation);

            rotated.put(rotatedPos, rotatedState);
        }

        return rotated;
    }

    /**
     * Rotates a position around the Y axis.
     * The rotation pivot is at the corner (0, y, 0).
     */
    private BlockPos rotatePosition(BlockPos pos, int rotation) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        return switch (rotation) {
            case 1 -> // 90° CW: (x, z) -> (sizeZ - z, x)
                new BlockPos(clipboardSizeZ - z, y, x);
            case 2 -> // 180°: (x, z) -> (sizeX - x, sizeZ - z)
                new BlockPos(clipboardSizeX - x, y, clipboardSizeZ - z);
            case 3 -> // 270° CW (90° CCW): (x, z) -> (z, sizeX - x)
                new BlockPos(z, y, clipboardSizeX - x);
            default -> pos;
        };
    }

    /**
     * Converts rotation index to BlockRotation enum.
     */
    private BlockRotation getBlockRotation(int rotation) {
        return switch (rotation) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    public boolean isPastePreviewActive() {
        return pastePreviewActive;
    }

    public void setPastePreviewActive(boolean active) {
        this.pastePreviewActive = active;
        if (!active) {
            previewAnchorPos = null;
        }
    }

    public BlockPos getPreviewAnchorPos() {
        return previewAnchorPos;
    }

    public void setPreviewAnchorPos(BlockPos pos) {
        this.previewAnchorPos = pos;
    }

    /**
     * Gets the current rotation (0-3, representing 0°, 90°, 180°, 270° CW).
     */
    public int getPreviewRotation() {
        return previewRotation;
    }

    /**
     * Sets the rotation and invalidates caches.
     */
    public void setPreviewRotation(int rotation) {
        this.previewRotation = rotation & 3; // Keep in range 0-3
    }

    /**
     * Rotates the preview clockwise by 90 degrees.
     */
    public void rotateClockwise() {
        previewRotation = (previewRotation + 1) & 3;
    }

    /**
     * Rotates the preview counter-clockwise by 90 degrees.
     */
    public void rotateCounterClockwise() {
        previewRotation = (previewRotation + 3) & 3; // +3 is same as -1 mod 4
    }

    /**
     * Resets rotation to 0 degrees.
     */
    public void resetRotation() {
        previewRotation = 0;
    }

    // ========== Layer View Methods (for locked placements) ==========

    public boolean isLayerViewEnabled() {
        return layerViewEnabled;
    }

    public void setLayerViewEnabled(boolean enabled) {
        this.layerViewEnabled = enabled;
        if (enabled && !lockedPlacements.isEmpty()) {
            // Reset to middle layer when enabling
            int maxY = getLockedPlacementsMaxY();
            currentViewLayer = maxY / 2;
        }
    }

    public void toggleLayerView() {
        setLayerViewEnabled(!layerViewEnabled);
    }

    public int getCurrentViewLayer() {
        return currentViewLayer;
    }

    public void setCurrentViewLayer(int layer) {
        this.currentViewLayer = layer;
    }

    public void cycleLayerUp() {
        if (lockedPlacements.isEmpty()) return;
        int maxY = getLockedPlacementsMaxY();
        if (currentViewLayer < maxY) {
            currentViewLayer++;
        }
    }

    public void cycleLayerDown() {
        if (currentViewLayer > 0) {
            currentViewLayer--;
        }
    }

    /**
     * Gets the maximum Y value across all locked placements (relative coordinates).
     */
    private int getLockedPlacementsMaxY() {
        int maxY = 0;
        for (LockedPlacement placement : lockedPlacements) {
            for (BlockPos pos : placement.getBlocks().keySet()) {
                if (pos.getY() > maxY) {
                    maxY = pos.getY();
                }
            }
        }
        return maxY;
    }

    /**
     * Gets the total number of layers in the locked placements.
     */
    public int getLayerCount() {
        if (lockedPlacements.isEmpty()) return 0;
        return getLockedPlacementsMaxY() + 1;
    }

    /**
     * Returns true if there are locked placements to view layers of.
     */
    public boolean hasLockedPlacements() {
        return !lockedPlacements.isEmpty();
    }

    /**
     * Locks the current preview position as a permanent ghost block placement.
     * Uses the current rotation.
     */
    public void lockCurrentPlacement() {
        if (previewAnchorPos == null || clipboardBlocks.isEmpty()) {
            return;
        }

        // Create a copy of the clipboard data with current rotation applied
        Map<BlockPos, BlockState> placementBlocks = new HashMap<>(getClipboardBlocks());
        lockedPlacements.add(new LockedPlacement(previewAnchorPos, placementBlocks));

        // Exit preview mode but keep rotation for next paste
        pastePreviewActive = false;
        previewAnchorPos = null;
    }

    public List<LockedPlacement> getLockedPlacements() {
        return lockedPlacements;
    }

    public void clearLockedPlacements() {
        // Clear each placement's block map before clearing the list to help GC
        for (LockedPlacement placement : lockedPlacements) {
            placement.clear();
        }
        lockedPlacements.clear();
        // Reset layer view since there's nothing to view
        layerViewEnabled = false;
        currentViewLayer = 0;
    }

    public void clearClipboard() {
        clipboardBlocks.clear();
        pastePreviewActive = false;
        previewAnchorPos = null;
    }

    /**
     * Represents a locked ghost block placement at a specific anchor position.
     */
    public static class LockedPlacement {
        private static long nextId = 0;

        private final long id;
        private final BlockPos anchorPos;
        private final Map<BlockPos, BlockState> blocks;
        // Pre-computed set of blocks that have at least one exposed face
        private Map<BlockPos, BlockState> visibleBlocks;

        public LockedPlacement(BlockPos anchorPos, Map<BlockPos, BlockState> blocks) {
            this.id = nextId++;
            this.anchorPos = anchorPos;
            this.blocks = blocks;
            this.visibleBlocks = null; // Computed lazily
        }

        public long getId() {
            return id;
        }

        public BlockPos getAnchorPos() {
            return anchorPos;
        }

        public Map<BlockPos, BlockState> getBlocks() {
            return blocks;
        }

        /**
         * Returns only blocks that have at least one exposed face (not surrounded on all sides).
         * This is computed once and cached.
         */
        public Map<BlockPos, BlockState> getVisibleBlocks() {
            if (visibleBlocks == null) {
                visibleBlocks = computeVisibleBlocks();
            }
            return visibleBlocks;
        }

        private Map<BlockPos, BlockState> computeVisibleBlocks() {
            Map<BlockPos, BlockState> visible = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                // Check if any adjacent position is empty (has exposed face)
                if (!blocks.containsKey(pos.up()) ||
                    !blocks.containsKey(pos.down()) ||
                    !blocks.containsKey(pos.north()) ||
                    !blocks.containsKey(pos.south()) ||
                    !blocks.containsKey(pos.east()) ||
                    !blocks.containsKey(pos.west())) {
                    visible.put(pos, entry.getValue());
                }
            }
            return visible;
        }

        /**
         * Clears the internal block map to help garbage collection.
         */
        void clear() {
            blocks.clear();
            if (visibleBlocks != null) {
                visibleBlocks.clear();
                visibleBlocks = null;
            }
        }
    }
}
