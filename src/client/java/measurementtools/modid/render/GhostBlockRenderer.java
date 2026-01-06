package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import measurementtools.modid.ClipboardManager;
import measurementtools.modid.ModConfig;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Map;

public class GhostBlockRenderer {
    // Reusable buffer allocators to prevent memory leaks and reduce allocations
    private BufferAllocator wireframeBuffer;
    private BufferAllocator solidBuffer;

    // Track buffer sizes for reallocation when needed
    private int lastSolidBlockCount = 0;
    private static final int WIREFRAME_BUFFER_SIZE = 16384;
    private static final int BYTES_PER_BLOCK = 2048; // Reduced from 4096
    private static final int MIN_SOLID_BUFFER_SIZE = 32768;

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

    private BufferAllocator getOrCreateWireframeBuffer() {
        if (wireframeBuffer == null) {
            wireframeBuffer = new BufferAllocator(WIREFRAME_BUFFER_SIZE);
        }
        return wireframeBuffer;
    }

    private BufferAllocator getOrCreateSolidBuffer(int blockCount) {
        int requiredSize = Math.max(MIN_SOLID_BUFFER_SIZE, blockCount * BYTES_PER_BLOCK);
        // Reallocate if we need more space or if count dropped significantly (to free memory)
        if (solidBuffer == null || lastSolidBlockCount * BYTES_PER_BLOCK < requiredSize / 2 || requiredSize > lastSolidBlockCount * BYTES_PER_BLOCK) {
            if (solidBuffer != null) {
                solidBuffer.close();
            }
            solidBuffer = new BufferAllocator(requiredSize);
            lastSolidBlockCount = blockCount;
        }
        return solidBuffer;
    }

    /**
     * Cleans up resources when the renderer is no longer needed.
     */
    public void cleanup() {
        if (wireframeBuffer != null) {
            wireframeBuffer.close();
            wireframeBuffer = null;
        }
        if (solidBuffer != null) {
            solidBuffer.close();
            solidBuffer = null;
        }
    }

    private void renderWireframeBlocks(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                       BlockPos anchor, Map<BlockPos, BlockState> blocks,
                                       boolean isPreview, float opacity) {
        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        BufferAllocator buffer = getOrCreateWireframeBuffer();
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
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

        // Use reusable buffer
        BufferAllocator buffer = getOrCreateSolidBuffer(blocks.size());
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);

        // Wrap the provider to apply alpha to all vertex colors
        VertexConsumerProvider alphaProvider = new AlphaVertexConsumerProvider(immediate, opacity, isPreview);

        // Enable polygon offset to prevent z-fighting with real blocks
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1.0f, -1.0f);

        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            // Skip blocks with no model
            if (state.getRenderType() == BlockRenderType.INVISIBLE) {
                continue;
            }

            // Calculate world position
            BlockPos worldPos = anchor.add(relativePos);

            // Get actual world lighting for this position (reduces brightness issues)
            int light = LightmapTextureManager.pack(
                world.getLightLevel(LightType.BLOCK, worldPos),
                world.getLightLevel(LightType.SKY, worldPos)
            );

            // Push matrix for this block
            matrices.push();

            // Translate to block position relative to camera
            matrices.translate(
                worldPos.getX() - cameraPos.x,
                worldPos.getY() - cameraPos.y,
                worldPos.getZ() - cameraPos.z
            );

            // Render the block using renderBlockAsEntity for standalone rendering
            blockRenderManager.renderBlockAsEntity(
                state,
                matrices,
                alphaProvider,
                light,
                OverlayTexture.DEFAULT_UV
            );

            matrices.pop();
        }

        // Draw all buffered block geometry
        immediate.draw();

        // Disable polygon offset
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    /**
     * A VertexConsumerProvider wrapper that forces use of translucent layer
     * and applies alpha transparency to all vertices via a wrapped consumer.
     */
    private static class AlphaVertexConsumerProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider.Immediate delegate;
        private final float alpha;
        private final boolean isPreview;

        public AlphaVertexConsumerProvider(VertexConsumerProvider.Immediate delegate, float alpha, boolean isPreview) {
            this.delegate = delegate;
            this.alpha = alpha;
            this.isPreview = isPreview;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            // Use translucent moving block layer for proper alpha support with shaders
            RenderLayer translucentLayer = RenderLayer.getTranslucentMovingBlock();
            return new AlphaVertexConsumer(delegate.getBuffer(translucentLayer), alpha, isPreview);
        }
    }

    /**
     * A VertexConsumer wrapper that modifies the alpha channel of all vertices.
     * This approach is shader-compatible as it modifies vertex data directly.
     */
    private static class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final boolean isPreview;

        public AlphaVertexConsumer(VertexConsumer delegate, float alpha, boolean isPreview) {
            this.delegate = delegate;
            this.alpha = (int) (alpha * 255) & 0xFF;
            this.isPreview = isPreview;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            // Apply preview tint if needed
            if (isPreview) {
                red = (int) (red * 0.85f);
                green = (int) (green * 0.85f);
                blue = Math.min(255, (int) (blue * 0.85f + 38)); // Add blue tint
            }
            // Use our alpha instead of the provided alpha
            delegate.color(red, green, blue, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
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
