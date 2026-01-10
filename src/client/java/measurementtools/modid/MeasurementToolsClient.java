package measurementtools.modid;

import measurementtools.modid.gui.BlockCountOverlay;
import measurementtools.modid.gui.RadialMenuRegistry;
import measurementtools.modid.gui.RadialMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class MeasurementToolsClient implements ClientModInitializer {
    private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(
        Identifier.of("measurementtools", "main")
    );
    private static final String KEY_MEASURE = "key.measurementtools.measure";

    private static KeyBinding keyBindingMeasure;

    private long measureKeyPressStart = 0;
    private boolean radialMenuOpened = false;
    private static final long HOLD_THRESHOLD_MS = 200;

    // Track arrow key states to detect press (not hold)
    private boolean leftArrowWasPressed = false;
    private boolean rightArrowWasPressed = false;
    private boolean upArrowWasPressed = false;
    private boolean downArrowWasPressed = false;
    private int lastPreviewRotation = 0;

    @Override
    public void onInitializeClient() {
        // Initialize radial menu actions
        RadialMenuRegistry.initialize();

        // Register keybinding
        keyBindingMeasure = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_MEASURE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
        ));

        // Handle input
        ClientTickEvents.END_CLIENT_TICK.register(this::handleInput);

        // Register HUD overlay for block counts
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            BlockCountOverlay.getInstance().render(context, tickCounter);
        });

        // Note: World rendering is handled by WorldRendererMixin
    }

    private void handleInput(MinecraftClient client) {
        // Update paste preview position every tick if active
        updatePastePreview(client);

        // Handle rotation keys when paste preview is active
        handleRotationInput(client);

        // Handle measure key (tap to add block OR confirm paste, hold to open radial menu)
        if (keyBindingMeasure.isPressed()) {
            if (measureKeyPressStart == 0) {
                measureKeyPressStart = System.currentTimeMillis();
                radialMenuOpened = false;
            } else if (!radialMenuOpened &&
                       System.currentTimeMillis() - measureKeyPressStart > HOLD_THRESHOLD_MS &&
                       client.currentScreen == null) {
                // Open radial menu after holding for threshold duration
                int keyCode = getKeyCode(keyBindingMeasure);
                client.setScreen(new RadialMenuScreen(keyCode));
                radialMenuOpened = true;
            }
        } else {
            // Key released
            if (measureKeyPressStart != 0 && !radialMenuOpened) {
                // Was a tap
                ClipboardManager clipboard = ClipboardManager.getInstance();
                if (clipboard.isPastePreviewActive()) {
                    // Confirm paste - lock the current placement
                    confirmPaste(client);
                } else {
                    // Normal behavior - add block to selection
                    addBlockToSelection(client);
                }
            }
            measureKeyPressStart = 0;
            radialMenuOpened = false;
        }
    }

    private void updatePastePreview(MinecraftClient client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        if (!clipboard.isPastePreviewActive()) return;

        if (client.world == null || client.getCameraEntity() == null) return;

        HitResult hitResult = client.getCameraEntity().raycast(512.0, 1.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            // Set anchor to the block adjacent to the hit face (where player would place a block)
            clipboard.setPreviewAnchorPos(blockHit.getBlockPos().offset(blockHit.getSide()));
        }
    }

    private void handleRotationInput(MinecraftClient client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        SelectionManager selection = SelectionManager.getInstance();

        if (client.currentScreen != null) {
            leftArrowWasPressed = false;
            rightArrowWasPressed = false;
            upArrowWasPressed = false;
            downArrowWasPressed = false;
            return;
        }

        // Handle layer mode for both hollow shapes and locked placements (when not in paste preview)
        if (!clipboard.isPastePreviewActive()) {
            boolean hollowLayerActive = selection.isLayerModeEnabled() && selection.hasSelection();
            boolean pasteLayerActive = clipboard.isLayerViewEnabled() && clipboard.hasLockedPlacements();

            if (hollowLayerActive || pasteLayerActive) {
                handleLayerInput(client, selection, clipboard, hollowLayerActive, pasteLayerActive);
            } else {
                upArrowWasPressed = false;
                downArrowWasPressed = false;
            }
        }

        // Handle paste preview rotation controls
        if (clipboard.isPastePreviewActive()) {
            // Check left arrow (rotate counter-clockwise)
            boolean leftPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT);
            if (leftPressed && !leftArrowWasPressed) {
                clipboard.rotateCounterClockwise();
                showRotationMessage(client, clipboard.getPreviewRotation());
            }
            leftArrowWasPressed = leftPressed;

            // Check right arrow (rotate clockwise)
            boolean rightPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT);
            if (rightPressed && !rightArrowWasPressed) {
                clipboard.rotateClockwise();
                showRotationMessage(client, clipboard.getPreviewRotation());
            }
            rightArrowWasPressed = rightPressed;

            // Check if rotation changed and invalidate render cache
            int currentRotation = clipboard.getPreviewRotation();
            if (currentRotation != lastPreviewRotation) {
                lastPreviewRotation = currentRotation;
                measurementtools.modid.render.MeasurementRenderer.getInstance().invalidateGhostBlockCaches();
            }
        } else {
            leftArrowWasPressed = false;
            rightArrowWasPressed = false;
        }
    }

    private void handleLayerInput(MinecraftClient client, SelectionManager selection,
                                   ClipboardManager clipboard, boolean hollowActive, boolean pasteActive) {
        boolean upPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_UP);
        if (upPressed && !upArrowWasPressed) {
            if (hollowActive) {
                selection.cycleLayerUp();
                measurementtools.modid.render.MeasurementRenderer.getInstance().invalidateHollowShapeCache();
            }
            if (pasteActive) {
                clipboard.cycleLayerUp();
            }
            showLayerMessage(client, selection, clipboard, hollowActive, pasteActive);
        }
        upArrowWasPressed = upPressed;

        boolean downPressed = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_DOWN);
        if (downPressed && !downArrowWasPressed) {
            if (hollowActive) {
                selection.cycleLayerDown();
                measurementtools.modid.render.MeasurementRenderer.getInstance().invalidateHollowShapeCache();
            }
            if (pasteActive) {
                clipboard.cycleLayerDown();
            }
            showLayerMessage(client, selection, clipboard, hollowActive, pasteActive);
        }
        downArrowWasPressed = downPressed;
    }

    private void showLayerMessage(MinecraftClient client, SelectionManager selection,
                                   ClipboardManager clipboard, boolean hollowActive, boolean pasteActive) {
        if (client.player == null) return;

        if (hollowActive && pasteActive) {
            // Show both
            int hLayer = selection.getCurrentLayer() + 1;
            int hTotal = selection.getLayerCount();
            int pLayer = clipboard.getCurrentViewLayer() + 1;
            int pTotal = clipboard.getLayerCount();
            client.player.sendMessage(Text.literal("Layer: " + hLayer + "/" + hTotal + " | Paste: " + pLayer + "/" + pTotal), true);
        } else if (hollowActive) {
            int layer = selection.getCurrentLayer() + 1;
            int total = selection.getLayerCount();
            client.player.sendMessage(Text.literal("Layer: " + layer + "/" + total + " (Y=" + selection.getCurrentLayerY() + ")"), true);
        } else if (pasteActive) {
            int layer = clipboard.getCurrentViewLayer() + 1;
            int total = clipboard.getLayerCount();
            client.player.sendMessage(Text.literal("Layer: " + layer + "/" + total), true);
        }
    }

    private void showRotationMessage(MinecraftClient client, int rotation) {
        if (client.player != null) {
            String degrees = switch (rotation) {
                case 0 -> "0°";
                case 1 -> "90°";
                case 2 -> "180°";
                case 3 -> "270°";
                default -> rotation * 90 + "°";
            };
            client.player.sendMessage(Text.literal("Rotation: " + degrees), true);
        }
    }

    private void confirmPaste(MinecraftClient client) {
        ClipboardManager clipboard = ClipboardManager.getInstance();

        if (clipboard.getPreviewAnchorPos() != null) {
            // Lock the current placement
            clipboard.lockCurrentPlacement();

            // Show confirmation message
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Ghost blocks placed"), true);
            }
        }
    }

    private void addBlockToSelection(MinecraftClient client) {
        if (client.world == null || client.player == null || client.getCameraEntity() == null) return;

        HitResult hitResult = client.getCameraEntity().raycast(512.0, 1.0F, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            SelectionManager.getInstance().addBlock(blockHit.getBlockPos());
        }
    }

    private int getKeyCode(KeyBinding keyBinding) {
        // Get the GLFW key code from the keybinding
        String translationKey = keyBinding.getBoundKeyTranslationKey();
        if (translationKey.startsWith("key.keyboard.")) {
            String keyName = translationKey.substring("key.keyboard.".length());
            return switch (keyName) {
                case "g" -> GLFW.GLFW_KEY_G;
                case "f" -> GLFW.GLFW_KEY_F;
                default -> GLFW.GLFW_KEY_G;
            };
        }
        return GLFW.GLFW_KEY_G;
    }
}
