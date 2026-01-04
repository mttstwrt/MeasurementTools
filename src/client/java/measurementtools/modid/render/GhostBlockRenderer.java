package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import measurementtools.modid.ClipboardManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Map;

public class GhostBlockRenderer {
    private static final float OUTLINE_ALPHA = 0.7f;

    public void render(Camera camera, Matrix4f viewMatrix) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;

        if (world == null) return;

        Vec3d cameraPos = camera.getPos();

        // Render preview blocks if paste preview is active
        if (clipboard.isPastePreviewActive() && clipboard.hasClipboardData()) {
            BlockPos anchor = clipboard.getPreviewAnchorPos();
            if (anchor != null) {
                renderGhostBlocks(camera, viewMatrix, cameraPos, world, anchor, clipboard.getClipboardBlocks(), true);
            }
        }

        // Render all locked placements
        for (ClipboardManager.LockedPlacement placement : clipboard.getLockedPlacements()) {
            renderGhostBlocks(camera, viewMatrix, cameraPos, world, placement.getAnchorPos(), placement.getBlocks(), false);
        }
    }

    private void renderGhostBlocks(Camera camera, Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                   BlockPos anchor, Map<BlockPos, BlockState> blocks, boolean isPreview) {
        if (blocks.isEmpty()) return;

        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(16384));
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            // Calculate world position
            BlockPos worldPos = anchor.add(relativePos);

            // Get block color from map color
            int mapColor = state.getMapColor(world, worldPos).color;
            float r = ((mapColor >> 16) & 0xFF) / 255f;
            float g = ((mapColor >> 8) & 0xFF) / 255f;
            float b = (mapColor & 0xFF) / 255f;

            // Make preview blocks slightly more transparent/different color
            if (isPreview) {
                // Add slight blue tint for preview
                r = r * 0.7f;
                g = g * 0.7f;
                b = Math.min(1.0f, b * 0.7f + 0.3f);
            }

            // Calculate block corners relative to camera
            float x1 = (float) (worldPos.getX() - cameraPos.x);
            float y1 = (float) (worldPos.getY() - cameraPos.y);
            float z1 = (float) (worldPos.getZ() - cameraPos.z);
            float x2 = x1 + 1;
            float y2 = y1 + 1;
            float z2 = z1 + 1;

            // Draw wireframe outline
            drawBlockOutline(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, OUTLINE_ALPHA);
        }

        immediate.draw();
    }

    private void drawBlockOutline(Matrix4f matrix, VertexConsumer lines,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        // Bottom face edges
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x2, y1, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z2, x2, y1, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x1, y1, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z1, x2, y1, z2, r, g, b, a);

        // Top face edges
        RenderUtils.drawLine(matrix, lines, x1, y2, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y2, z2, x2, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y2, z1, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y2, z1, x2, y2, z2, r, g, b, a);

        // Vertical edges
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x1, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z2, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z2, x2, y2, z2, r, g, b, a);
    }
}
