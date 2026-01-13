package measurementtools.modid;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Manages undo/redo history for block selections and locked paste placements.
 */
public class UndoRedoManager {
    private static final UndoRedoManager INSTANCE = new UndoRedoManager();
    private static final int MAX_HISTORY_SIZE = 50;

    private final Deque<HistoryState> undoStack = new ArrayDeque<>();
    private final Deque<HistoryState> redoStack = new ArrayDeque<>();

    // Flag to prevent recording during undo/redo operations
    private boolean isUndoRedoInProgress = false;

    private UndoRedoManager() {}

    public static UndoRedoManager getInstance() {
        return INSTANCE;
    }

    /**
     * Saves the current state before a change is made.
     * Call this BEFORE modifying selections or placements.
     */
    public void saveState() {
        if (isUndoRedoInProgress) {
            return;
        }

        SelectionManager selection = SelectionManager.getInstance();
        ClipboardManager clipboard = ClipboardManager.getInstance();

        HistoryState state = new HistoryState(
            new ArrayList<>(selection.getSelectedBlocks()),
            copyLockedPlacements(clipboard.getLockedPlacements())
        );

        undoStack.push(state);

        // Limit history size
        while (undoStack.size() > MAX_HISTORY_SIZE) {
            undoStack.removeLast();
        }

        // Clear redo stack when new action is taken
        redoStack.clear();
    }

    /**
     * Undoes the last action, restoring the previous state.
     * @return true if undo was successful, false if nothing to undo
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        isUndoRedoInProgress = true;
        try {
            // Save current state to redo stack
            SelectionManager selection = SelectionManager.getInstance();
            ClipboardManager clipboard = ClipboardManager.getInstance();

            HistoryState currentState = new HistoryState(
                new ArrayList<>(selection.getSelectedBlocks()),
                copyLockedPlacements(clipboard.getLockedPlacements())
            );
            redoStack.push(currentState);

            // Restore previous state
            HistoryState previousState = undoStack.pop();
            restoreState(previousState);

            return true;
        } finally {
            isUndoRedoInProgress = false;
        }
    }

    /**
     * Redoes the last undone action.
     * @return true if redo was successful, false if nothing to redo
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }

        isUndoRedoInProgress = true;
        try {
            // Save current state to undo stack
            SelectionManager selection = SelectionManager.getInstance();
            ClipboardManager clipboard = ClipboardManager.getInstance();

            HistoryState currentState = new HistoryState(
                new ArrayList<>(selection.getSelectedBlocks()),
                copyLockedPlacements(clipboard.getLockedPlacements())
            );
            undoStack.push(currentState);

            // Restore redo state
            HistoryState redoState = redoStack.pop();
            restoreState(redoState);

            return true;
        } finally {
            isUndoRedoInProgress = false;
        }
    }

    /**
     * Returns true if there are actions that can be undone.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Returns true if there are actions that can be redone.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clears all history.
     */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Restores the selection and clipboard state from a history snapshot.
     */
    private void restoreState(HistoryState state) {
        SelectionManager selection = SelectionManager.getInstance();
        ClipboardManager clipboard = ClipboardManager.getInstance();

        // Restore selections
        selection.setSelectedBlocks(state.selectedBlocks);

        // Restore locked placements
        clipboard.setLockedPlacements(state.lockedPlacements);
    }

    /**
     * Creates a deep copy of locked placements for history storage.
     */
    private List<LockedPlacementData> copyLockedPlacements(List<ClipboardManager.LockedPlacement> placements) {
        List<LockedPlacementData> copy = new ArrayList<>();
        for (ClipboardManager.LockedPlacement placement : placements) {
            copy.add(new LockedPlacementData(
                placement.getAnchorPos(),
                new HashMap<>(placement.getBlocks())
            ));
        }
        return copy;
    }

    /**
     * Represents a snapshot of the state at a point in time.
     */
    private static class HistoryState {
        final List<BlockPos> selectedBlocks;
        final List<LockedPlacementData> lockedPlacements;

        HistoryState(List<BlockPos> selectedBlocks, List<LockedPlacementData> lockedPlacements) {
            this.selectedBlocks = selectedBlocks;
            this.lockedPlacements = lockedPlacements;
        }
    }

    /**
     * Data class to store locked placement info for history.
     */
    static class LockedPlacementData {
        final BlockPos anchorPos;
        final Map<BlockPos, BlockState> blocks;

        LockedPlacementData(BlockPos anchorPos, Map<BlockPos, BlockState> blocks) {
            this.anchorPos = anchorPos;
            this.blocks = blocks;
        }
    }
}
