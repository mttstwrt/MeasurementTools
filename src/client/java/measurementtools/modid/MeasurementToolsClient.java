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
        // Handle measure key (tap to add block, hold to open radial menu)
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
                // Was a tap - add block to selection
                addBlockToSelection(client);
            }
            measureKeyPressStart = 0;
            radialMenuOpened = false;
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
