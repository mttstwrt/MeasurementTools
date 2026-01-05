package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import measurementtools.modid.ClipboardManager;
import measurementtools.modid.ModConfig;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Map;

public class GhostBlockRenderer {
    private final Random random = Random.create();

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

        ModConfig config = ModConfig.getInstance();
        ModConfig.GhostBlockRenderMode renderMode = config.getGhostBlockRenderMode();
        float opacity = config.getGhostBlockOpacity();

        if (renderMode == ModConfig.GhostBlockRenderMode.SOLID) {
            renderSolidBlocks(viewMatrix, cameraPos, world, anchor, blocks, isPreview, opacity);
        } else {
            renderWireframeBlocks(viewMatrix, cameraPos, world, anchor, blocks, isPreview, opacity);
        }
    }

    private void renderWireframeBlocks(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                       BlockPos anchor, Map<BlockPos, BlockState> blocks,
                                       boolean isPreview, float opacity) {
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
            drawBlockOutline(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, opacity);
        }

        immediate.draw();
    }

    private void renderSolidBlocks(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                   BlockPos anchor, Map<BlockPos, BlockState> blocks,
                                   boolean isPreview, float opacity) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        // Create a vertex consumer provider with a large buffer for block rendering
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(blocks.size() * 1024));

        // Calculate the alpha tint color (white with variable alpha for transparency)
        float tintR = 1.0f;
        float tintG = 1.0f;
        float tintB = 1.0f;
        if (isPreview) {
            // Slight blue tint for preview
            tintR = 0.8f;
            tintG = 0.8f;
            tintB = 1.0f;
        }

        // Full brightness for ghost blocks
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            // Skip blocks with no model
            if (state.getRenderType() == BlockRenderType.INVISIBLE) {
                continue;
            }

            // Calculate world position
            BlockPos worldPos = anchor.add(relativePos);

            // Push matrix for this block
            matrices.push();

            // Translate to block position relative to camera
            matrices.translate(
                worldPos.getX() - cameraPos.x,
                worldPos.getY() - cameraPos.y,
                worldPos.getZ() - cameraPos.z
            );

            // Render the block using renderBlockAsEntity for standalone rendering
            // This renders the block model with its actual textures
            blockRenderManager.renderBlockAsEntity(
                state,
                matrices,
                immediate,
                light,
                OverlayTexture.DEFAULT_UV
            );

            matrices.pop();
        }

        // Draw all buffered block geometry
        immediate.draw();

        // If opacity is less than 1, draw a translucent overlay to fade the blocks
        if (opacity < 1.0f) {
            renderOpacityOverlay(viewMatrix, cameraPos, world, anchor, blocks, isPreview, opacity);
        }
    }

    private void renderOpacityOverlay(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                      BlockPos anchor, Map<BlockPos, BlockState> blocks,
                                      boolean isPreview, float opacity) {
        // For translucency, we render a semi-transparent overlay on top
        // This creates the effect of the blocks being see-through
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(blocks.size() * 6 * 4 * 32));
        VertexConsumer quads = immediate.getBuffer(RenderLayer.getDebugFilledBox());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Inverse opacity - we draw dark overlay to fade out the blocks
        float overlayAlpha = 1.0f - opacity;

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            if (state.getRenderType() == BlockRenderType.INVISIBLE) {
                continue;
            }

            // Calculate world position
            BlockPos worldPos = anchor.add(relativePos);

            // Calculate block corners relative to camera (slightly larger to cover the block)
            float x1 = (float) (worldPos.getX() - cameraPos.x) - 0.001f;
            float y1 = (float) (worldPos.getY() - cameraPos.y) - 0.001f;
            float z1 = (float) (worldPos.getZ() - cameraPos.z) - 0.001f;
            float x2 = x1 + 1.002f;
            float y2 = y1 + 1.002f;
            float z2 = z1 + 1.002f;

            // Draw a dark overlay to create fade effect
            // Using sky color (or could use a config color)
            float r = 0.5f;
            float g = 0.6f;
            float b = 0.7f;
            if (isPreview) {
                // Slight blue for preview
                b = 0.9f;
            }

            drawSolidBlock(matrix, quads, x1, y1, z1, x2, y2, z2, r, g, b, overlayAlpha);
        }

        immediate.draw();
    }

    private void drawSolidBlock(Matrix4f matrix, VertexConsumer quads,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b, float a) {
        // Bottom face (y1) - counter-clockwise from above
        quads.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        quads.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y1, z1).color(r, g, b, a);

        // Top face (y2) - counter-clockwise from above
        quads.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        quads.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        // North face (z1) - facing -Z
        quads.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        quads.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        quads.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // South face (z2) - facing +Z
        quads.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        quads.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y1, z2).color(r, g, b, a);

        // West face (x1) - facing -X
        quads.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        quads.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        quads.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        quads.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        // East face (x2) - facing +X
        quads.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        quads.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        quads.vertex(matrix, x2, y2, z1).color(r, g, b, a);
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
