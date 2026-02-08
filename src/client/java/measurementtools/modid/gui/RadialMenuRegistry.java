package measurementtools.modid.gui;

import measurementtools.modid.ClipboardManager;
import measurementtools.modid.SelectionManager;
import measurementtools.modid.UndoRedoManager;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RadialMenuRegistry {
    private static final List<RadialMenuAction> actions = new ArrayList<>();

    public static void register(RadialMenuAction action) {
        actions.add(action);
    }

    public static List<RadialMenuAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public static void initialize() {
        // Clear any existing actions (in case of reload)
        actions.clear();

        // Clear Selection (also clears locked ghost placements)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Clear");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().clearSelection();
                ClipboardManager.getInstance().clearLockedPlacements();
            }

            @Override
            public boolean isEnabled() {
                return SelectionManager.getInstance().hasSelection() ||
                       !ClipboardManager.getInstance().getLockedPlacements().isEmpty();
            }

            @Override
            public int getColor() {
                return 0xFF6666;
            }
        });

        // Copy Selection
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Copy");
            }

            @Override
            public void execute() {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    ClipboardManager.getInstance().copySelection(client.world);
                }
            }

            @Override
            public boolean isEnabled() {
                return SelectionManager.getInstance().hasSelection();
            }

            @Override
            public int getColor() {
                return ClipboardManager.getInstance().hasClipboardData()
                    ? 0x66FFFF : 0xFFFFFF;
            }
        });

        // Paste (toggle preview mode)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                ClipboardManager clipboard = ClipboardManager.getInstance();
                if (clipboard.isPastePreviewActive()) {
                    return Text.literal("Cancel Paste");
                }
                return Text.literal("Paste");
            }

            @Override
            public void execute() {
                ClipboardManager clipboard = ClipboardManager.getInstance();
                if (clipboard.isPastePreviewActive()) {
                    // Cancel paste preview
                    clipboard.setPastePreviewActive(false);
                } else {
                    // Start paste preview
                    clipboard.setPastePreviewActive(true);
                }
            }

            @Override
            public boolean isEnabled() {
                return ClipboardManager.getInstance().hasClipboardData();
            }

            @Override
            public int getColor() {
                ClipboardManager clipboard = ClipboardManager.getInstance();
                if (clipboard.isPastePreviewActive()) {
                    return 0xFF6666; // Red when canceling
                }
                return clipboard.hasClipboardData() ? 0x66FF66 : 0x888888;
            }
        });

        // Rectangle Mode (with subdivision scroll support)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                SelectionManager manager = SelectionManager.getInstance();
                if (manager.getShapeMode() == ShapeMode.RECTANGLE) {
                    int count = manager.getSubdivisionCount();
                    if (count == 0) {
                        return Text.literal("Rectangle");
                    }
                    return Text.literal("Rectangle ÷" + count);
                }
                return Text.literal("Rectangle");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().setShapeMode(ShapeMode.RECTANGLE);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                SelectionManager manager = SelectionManager.getInstance();
                if (manager.getShapeMode() == ShapeMode.RECTANGLE) {
                    return manager.getSubdivisionCount() > 0 ? 0xFFFF66 : 0x66FF66;
                }
                return 0xFFFFFF;
            }

            @Override
            public boolean onScroll(double amount) {
                if (amount > 0) {
                    SelectionManager.getInstance().stepSubdivisionUp();
                } else if (amount < 0) {
                    SelectionManager.getInstance().stepSubdivisionDown();
                }
                return true;
            }
        });

        // Cylinder Mode
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Cylinder");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().setShapeMode(ShapeMode.CYLINDER);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().getShapeMode() == ShapeMode.CYLINDER
                    ? 0x66FF66 : 0xFFFFFF;
            }
        });

        // Ellipsoid Mode
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Ellipsoid");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().setShapeMode(ShapeMode.ELLIPSOID);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().getShapeMode() == ShapeMode.ELLIPSOID
                    ? 0x66FF66 : 0xFFFFFF;
            }
        });

        // Spline Mode (with radius scroll support for tubes/tunnels)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                SelectionManager manager = SelectionManager.getInstance();
                if (manager.getShapeMode() == ShapeMode.SPLINE) {
                    int radius = manager.getSplineRadius();
                    if (radius == 0) {
                        return Text.literal("Spline");
                    }
                    return Text.literal("Spline r=" + radius);
                }
                return Text.literal("Spline");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().setShapeMode(ShapeMode.SPLINE);
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                SelectionManager manager = SelectionManager.getInstance();
                if (manager.getShapeMode() == ShapeMode.SPLINE) {
                    return manager.getSplineRadius() > 0 ? 0xFFFF66 : 0x66FF66;
                }
                return 0xFFFFFF;
            }

            @Override
            public boolean onScroll(double amount) {
                if (amount > 0) {
                    SelectionManager.getInstance().stepSplineRadiusUp();
                } else if (amount < 0) {
                    SelectionManager.getInstance().stepSplineRadiusDown();
                }
                return true;
            }
        });

        // Block Count Toggle
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                boolean enabled = SelectionManager.getInstance().isBlockCountingEnabled();
                return Text.literal(enabled ? "Count: On" : "Count: Off");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().toggleBlockCounting();
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().isBlockCountingEnabled()
                    ? 0x66CCFF : 0xFFFFFF;
            }
        });

        // Hollow Mode Toggle
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                boolean enabled = SelectionManager.getInstance().isHollowMode();
                return Text.literal(enabled ? "Hollow: On" : "Hollow: Off");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().toggleHollowMode();
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().isHollowMode()
                    ? 0xFF9966 : 0xFFFFFF;
            }
        });

        // Layer Mode Toggle (affects both hollow shapes and locked placements)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();
                boolean enabled = selection.isLayerModeEnabled() || clipboard.isLayerViewEnabled();
                return Text.literal(enabled ? "Layer: On" : "Layer: Off");
            }

            @Override
            public void execute() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();

                // Determine current state (either being on means it's "on")
                boolean currentlyEnabled = selection.isLayerModeEnabled() || clipboard.isLayerViewEnabled();
                boolean newState = !currentlyEnabled;

                // Apply to both if applicable
                if (selection.hasSelection()) {
                    selection.setLayerModeEnabled(newState);
                }
                if (clipboard.hasLockedPlacements()) {
                    clipboard.setLayerViewEnabled(newState);
                }
            }

            @Override
            public boolean isEnabled() {
                // Enable if we have a selection (for hollow) or locked placements
                return SelectionManager.getInstance().hasSelection() ||
                       ClipboardManager.getInstance().hasLockedPlacements();
            }

            @Override
            public int getColor() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();
                boolean active = selection.isLayerModeEnabled() || clipboard.isLayerViewEnabled();
                return active ? 0x66FF99 : 0xFFFFFF;
            }
        });

        // Chunk Boundaries Toggle
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                boolean enabled = SelectionManager.getInstance().isChunkBoundariesEnabled();
                return Text.literal(enabled ? "Chunks: On" : "Chunks: Off");
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().toggleChunkBoundaries();
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().isChunkBoundariesEnabled()
                    ? 0xFFFF66 : 0xFFFFFF;
            }
        });

        // Undo
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Undo");
            }

            @Override
            public void execute() {
                UndoRedoManager.getInstance().undo();
            }

            @Override
            public boolean isEnabled() {
                return UndoRedoManager.getInstance().canUndo();
            }

            @Override
            public int getColor() {
                return UndoRedoManager.getInstance().canUndo() ? 0xFFCC66 : 0x888888;
            }
        });

        // Redo
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                return Text.literal("Redo");
            }

            @Override
            public void execute() {
                UndoRedoManager.getInstance().redo();
            }

            @Override
            public boolean isEnabled() {
                return UndoRedoManager.getInstance().canRedo();
            }

            @Override
            public int getColor() {
                return UndoRedoManager.getInstance().canRedo() ? 0x66CCFF : 0x888888;
            }
        });
    }
}
