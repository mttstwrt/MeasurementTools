package measurementtools.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Vector;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicReference;


import net.fabricmc.api.ClientModInitializer;

public class MeasurementToolsClient implements ClientModInitializer {
	private static final String KEY_CATEGORY = "key.category.blockhighlighter";
	private static final String KEY_HIGHLIGHT = "key.blockhighlighter.highlight";
	private static final String KEY_CLEAR = "key.blockhighlighter.clear";

	private static KeyBinding keyBindingAddBlock;
	private static KeyBinding keyBindingClear;
	private static List<AtomicReference<BlockPos>> targetedBlocks = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		// Register keybinding (default to 'F')
		keyBindingAddBlock = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				KEY_HIGHLIGHT,
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				KEY_CATEGORY
		));
		keyBindingClear = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				KEY_CLEAR,
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				KEY_CATEGORY
		));

		// Handle key presses
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (keyBindingAddBlock.isPressed()) {
				addHighlight(client);
			}
			if (keyBindingClear.isPressed()) {
				clearHighlight(client);
			}
		});

		// Register render event
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderHighlight);
	}

	private void addHighlight(MinecraftClient client) {
		if (client.world == null) return;

		AtomicReference<BlockPos> targetBlock = new AtomicReference<>(null);
		targetBlock.set(getTargetBlock(client));

		if (targetBlock.get() != null) {
			targetedBlocks.add(targetBlock);
		}
	}

	private void clearHighlight(MinecraftClient client) {
		targetedBlocks.clear();
	}

	private BlockPos getTargetBlock(MinecraftClient client) {
		// Get player's position and look vector
		assert client.player != null;
		Vec3d eyePos = client.player.getEyePos();
		Vec3d lookVec = client.player.getRotationVec(1.0F);

		double range = 512.0D;

		assert client.world != null;
		assert client.cameraEntity != null;
		BlockHitResult hitResult = (BlockHitResult) client.cameraEntity.raycast(range, 1.0F, false);

		// Return block position if found, null otherwise
		if (hitResult.getType() == HitResult.Type.BLOCK) {
			return hitResult.getBlockPos();
		}
		return null;
	}

	private void renderHighlight(WorldRenderContext context) {
		for (AtomicReference<BlockPos> targetedBlock : targetedBlocks) {
			// Only render if highlight is enabled and there's a target block
			if (targetedBlock.get() == null) return;

			// Only render if the block still exists
			BlockPos pos = targetedBlock.get();

			// Get rendering context
			MatrixStack matrices = context.matrixStack();
			Vec3d camera = context.camera().getPos();
			VertexConsumerProvider consumers = context.consumers();

			if (consumers == null) return;

			// Push matrix and translate to block position
			assert matrices != null;
			matrices.push();
			matrices.translate(
					pos.getX() - camera.x,
					pos.getY() - camera.y,
					pos.getZ() - camera.z
			);

			// Get the block's outline shape for exact dimensions
			Box box = new Box(0, 0, 0, 1, 1, 1);

			// Define highlight colors (RGBA)
			float red = 1.0F;
			float green = 0.3F;
			float blue = 0.3F;
			float alpha = 0.8F;

			// Draw block outline
			VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
			drawBox(matrices, lines, box, red, green, blue, alpha);

			matrices.pop();
		}
	}

	private void drawBox(MatrixStack matrices, VertexConsumer lines, Box box, float red, float green, float blue, float alpha) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float minX = (float)box.minX;
		float minY = (float)box.minY;
		float minZ = (float)box.minZ;
		float maxX = (float)box.maxX;
		float maxY = (float)box.maxY;
		float maxZ = (float)box.maxZ;

		// Bottom face
		drawLine(matrix, lines, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
		drawLine(matrix, lines, minX, minY, maxZ, maxX, minY, maxZ, red, green, blue, alpha);
		drawLine(matrix, lines, minX, minY, minZ, minX, minY, maxZ, red, green, blue, alpha);
		drawLine(matrix, lines, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);

		// Top face
		drawLine(matrix, lines, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
		drawLine(matrix, lines, minX, maxY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
		drawLine(matrix, lines, minX, maxY, minZ, minX, maxY, maxZ, red, green, blue, alpha);
		drawLine(matrix, lines, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);

		// Connecting edges
		drawLine(matrix, lines, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
		drawLine(matrix, lines, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
		drawLine(matrix, lines, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
		drawLine(matrix, lines, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
	}

	private void drawLine(Matrix4f matrix, VertexConsumer lines, float x1, float y1, float z1, float x2, float y2, float z2, float red, float green, float blue, float alpha) {
		lines.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(1, 0, 0);
		lines.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(1, 0, 0);
	}
}