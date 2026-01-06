package measurementtools.modid;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
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

    // Locked placements: each entry is (anchorPos, relativeBlocks with states)
    private final List<LockedPlacement> lockedPlacements = new ArrayList<>();

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

    public Map<BlockPos, BlockState> getClipboardBlocks() {
        return clipboardBlocks;
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
     * Locks the current preview position as a permanent ghost block placement.
     */
    public void lockCurrentPlacement() {
        if (previewAnchorPos == null || clipboardBlocks.isEmpty()) {
            return;
        }

        // Create a copy of the clipboard data for this locked placement
        Map<BlockPos, BlockState> placementBlocks = new HashMap<>(clipboardBlocks);
        lockedPlacements.add(new LockedPlacement(previewAnchorPos, placementBlocks));

        // Exit preview mode
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
        private final BlockPos anchorPos;
        private final Map<BlockPos, BlockState> blocks;

        public LockedPlacement(BlockPos anchorPos, Map<BlockPos, BlockState> blocks) {
            this.anchorPos = anchorPos;
            this.blocks = blocks;
        }

        public BlockPos getAnchorPos() {
            return anchorPos;
        }

        public Map<BlockPos, BlockState> getBlocks() {
            return blocks;
        }

        /**
         * Clears the internal block map to help garbage collection.
         */
        void clear() {
            blocks.clear();
        }
    }
}
