package measurementtools.modid.gui;

import measurementtools.modid.ClipboardManager;
import measurementtools.modid.SelectionManager;
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

        // Rectangle Mode
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
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
                return SelectionManager.getInstance().getShapeMode() == ShapeMode.RECTANGLE
                    ? 0x66FF66 : 0xFFFFFF;
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

        // Subdivision Toggle
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                int count = SelectionManager.getInstance().getSubdivisionCount();
                if (count == 0) {
                    return Text.literal("Subdivide: Off");
                }
                return Text.literal("Subdivide: " + count);
            }

            @Override
            public void execute() {
                SelectionManager.getInstance().cycleSubdivision();
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public int getColor() {
                return SelectionManager.getInstance().getSubdivisionCount() > 0
                    ? 0xFFFF66 : 0xFFFFFF;
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

        // Layer Mode Toggle (for hollow shapes)
        register(new RadialMenuAction() {
            @Override
            public Text getName() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();

                // Show different label based on context
                if (clipboard.isPastePreviewActive()) {
                    boolean enabled = clipboard.isLayerViewEnabled();
                    return Text.literal(enabled ? "Paste Layer: On" : "Paste Layer: Off");
                } else {
                    boolean enabled = selection.isLayerModeEnabled();
                    return Text.literal(enabled ? "Layer: On" : "Layer: Off");
                }
            }

            @Override
            public void execute() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();

                // Toggle appropriate layer mode based on context
                if (clipboard.isPastePreviewActive()) {
                    clipboard.toggleLayerView();
                } else {
                    selection.toggleLayerMode();
                }
            }

            @Override
            public boolean isEnabled() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();
                // Enable if we have a selection (for hollow) or paste preview is active
                return selection.hasSelection() || clipboard.isPastePreviewActive();
            }

            @Override
            public int getColor() {
                SelectionManager selection = SelectionManager.getInstance();
                ClipboardManager clipboard = ClipboardManager.getInstance();

                boolean active = false;
                if (clipboard.isPastePreviewActive()) {
                    active = clipboard.isLayerViewEnabled();
                } else {
                    active = selection.isLayerModeEnabled();
                }
                return active ? 0x66FF99 : 0xFFFFFF;
            }
        });
    }
}
