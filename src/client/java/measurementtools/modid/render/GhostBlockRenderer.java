package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import measurementtools.modid.ClipboardManager;
import measurementtools.modid.ModConfig;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders ghost blocks for copy/paste preview and locked placements.
 *
 * Uses CPU-side vertex caching to avoid expensive per-block renderBlockAsEntity
 * calls every frame. Vertex data is captured once and replayed each frame,
 * significantly reducing CPU overhead for large selections.
 */
public class GhostBlockRenderer {
    // Reusable buffer allocator for wireframe rendering
    private BufferAllocator wireframeBuffer;
    private static final int WIREFRAME_BUFFER_SIZE = 16384;

    // Buffer for solid block rendering (sized dynamically)
    private BufferAllocator solidBuffer;
    private int lastSolidBlockCount = 0;
    private static final int BYTES_PER_BLOCK = 2048;
    private static final int MIN_SOLID_BUFFER_SIZE = 32768;

    // Cached vertex data for preview
    private CachedVertexData previewCache;
    private int lastPreviewClipboardHash = 0;
    private float lastPreviewOpacity = -1;

    // Cached vertex data for locked placements (keyed by placement ID)
    private final Map<Long, CachedVertexData> lockedPlacementCaches = new HashMap<>();
    private float lastLockedOpacity = -1;

    // Maximum render distance for ghost blocks (in blocks)
    private static final double MAX_RENDER_DISTANCE_SQ = 128.0 * 128.0;

    // Constant light level for ghost blocks (moderately bright)
    private static final int GHOST_BLOCK_LIGHT = LightmapTextureManager.pack(12, 15);

    // Cache for visible blocks in preview
    private Map<BlockPos, BlockState> cachedPreviewVisibleBlocks;
    private int lastClipboardSize = -1;

    // Frustum for culling (set each frame)
    private Frustum frustum;

    public void setFrustum(Frustum frustum) {
        this.frustum = frustum;
    }

    public void render(Camera camera, Matrix4f viewMatrix) {
        ClipboardManager clipboard = ClipboardManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;

        if (world == null) return;

        Vec3d cameraPos = camera.getPos();
        ModConfig config = ModConfig.getInstance();
        ModConfig.GhostBlockRenderMode renderMode = config.getGhostBlockRenderMode();
        float opacity = config.getGhostBlockOpacity();

        // Render preview blocks if paste preview is active
        if (clipboard.isPastePreviewActive() && clipboard.hasClipboardData()) {
            BlockPos anchor = clipboard.getPreviewAnchorPos();
            if (anchor != null) {
                Map<BlockPos, BlockState> visibleBlocks = getPreviewVisibleBlocks(clipboard.getClipboardBlocks());

                if (renderMode == ModConfig.GhostBlockRenderMode.SOLID) {
                    renderSolidBlocksCached(viewMatrix, cameraPos, world, anchor, visibleBlocks, true, opacity);
                } else {
                    renderWireframeBlocks(viewMatrix, cameraPos, world, anchor, visibleBlocks, true, opacity);
                }
            }
        }

        // Render all locked placements
        for (ClipboardManager.LockedPlacement placement : clipboard.getLockedPlacements()) {
            if (renderMode == ModConfig.GhostBlockRenderMode.SOLID) {
                renderLockedPlacementCached(viewMatrix, cameraPos, world, placement, opacity);
            } else {
                renderWireframeBlocks(viewMatrix, cameraPos, world,
                    placement.getAnchorPos(), placement.getVisibleBlocks(), false, opacity);
            }
        }

        // Clean up caches for removed placements
        cleanupOrphanedCaches(clipboard);
    }

    /**
     * Renders solid ghost blocks using cached vertex data.
     * Cache is rebuilt only when clipboard content or opacity changes.
     */
    private void renderSolidBlocksCached(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                         BlockPos anchor, Map<BlockPos, BlockState> blocks,
                                         boolean isPreview, float opacity) {
        if (blocks.isEmpty()) return;

        // Check if anchor is within render distance
        if (!isAnchorVisible(anchor, cameraPos)) {
            return;
        }

        // Check if we need to rebuild the cache
        int clipboardHash = blocks.hashCode();
        boolean needsRebuild = previewCache == null ||
                              clipboardHash != lastPreviewClipboardHash ||
                              opacity != lastPreviewOpacity;

        if (needsRebuild) {
            previewCache = buildVertexCache(world, blocks, isPreview, opacity);
            lastPreviewClipboardHash = clipboardHash;
            lastPreviewOpacity = opacity;
        }

        if (previewCache != null && !previewCache.isEmpty()) {
            drawCachedData(viewMatrix, cameraPos, anchor, previewCache, blocks.size());
        }
    }

    /**
     * Renders a locked placement using cached vertex data.
     */
    private void renderLockedPlacementCached(Matrix4f viewMatrix, Vec3d cameraPos, World world,
                                             ClipboardManager.LockedPlacement placement, float opacity) {
        Map<BlockPos, BlockState> blocks = placement.getVisibleBlocks();
        if (blocks.isEmpty()) return;

        BlockPos anchor = placement.getAnchorPos();

        // Check render distance
        if (!isAnchorVisible(anchor, cameraPos)) {
            return;
        }

        long placementId = placement.getId();

        // Check if we need to rebuild (new placement or opacity changed)
        CachedVertexData cache = lockedPlacementCaches.get(placementId);
        boolean needsRebuild = cache == null || opacity != lastLockedOpacity;

        if (needsRebuild) {
            cache = buildVertexCache(world, blocks, false, opacity);
            lockedPlacementCaches.put(placementId, cache);
        }

        if (cache != null && !cache.isEmpty()) {
            drawCachedData(viewMatrix, cameraPos, anchor, cache, blocks.size());
        }

        lastLockedOpacity = opacity;
    }

    /**
     * Builds cached vertex data for a set of blocks.
     * This captures all vertex data from renderBlockAsEntity calls.
     */
    private CachedVertexData buildVertexCache(World world, Map<BlockPos, BlockState> blocks,
                                              boolean isPreview, float opacity) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager blockRenderManager = client.getBlockRenderManager();

        CachedVertexData cache = new CachedVertexData();
        CachingVertexConsumer captureConsumer = new CachingVertexConsumer(cache, opacity, isPreview);
        CachingVertexConsumerProvider captureProvider = new CachingVertexConsumerProvider(captureConsumer);

        MatrixStack matrices = new MatrixStack();

        // Build all blocks at relative positions
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockState state = entry.getValue();

            if (state.getRenderType() == BlockRenderType.INVISIBLE) {
                continue;
            }

            matrices.push();
            matrices.translate(relativePos.getX(), relativePos.getY(), relativePos.getZ());

            // Capture vertex data
            blockRenderManager.renderBlockAsEntity(
                state,
                matrices,
                captureProvider,
                GHOST_BLOCK_LIGHT,
                OverlayTexture.DEFAULT_UV
            );

            matrices.pop();
        }

        return cache;
    }

    /**
     * Draws cached vertex data at the specified anchor position.
     */
    private void drawCachedData(Matrix4f viewMatrix, Vec3d cameraPos, BlockPos anchor,
                                CachedVertexData cache, int blockCount) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        // Translate to anchor position (camera-relative)
        matrices.translate(
            anchor.getX() - cameraPos.x,
            anchor.getY() - cameraPos.y,
            anchor.getZ() - cameraPos.z
        );

        BufferAllocator buffer = getOrCreateSolidBuffer(blockCount);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer consumer = immediate.getBuffer(RenderLayer.getTranslucentMovingBlock());

        // Enable polygon offset to prevent z-fighting
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1.0f, -1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Replay cached vertex data
        cache.replay(consumer, matrix);

        immediate.draw();

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    /**
     * Removes caches for placements that no longer exist.
     */
    private void cleanupOrphanedCaches(ClipboardManager clipboard) {
        var placements = clipboard.getLockedPlacements();
        var placementIds = new java.util.HashSet<Long>();
        for (var placement : placements) {
            placementIds.add(placement.getId());
        }

        var iterator = lockedPlacementCaches.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!placementIds.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    /**
     * Checks if an anchor position is within render distance.
     */
    private boolean isAnchorVisible(BlockPos anchor, Vec3d cameraPos) {
        double dx = anchor.getX() - cameraPos.x;
        double dy = anchor.getY() - cameraPos.y;
        double dz = anchor.getZ() - cameraPos.z;
        return dx * dx + dy * dy + dz * dz < MAX_RENDER_DISTANCE_SQ;
    }

    /**
     * Gets visible blocks for preview, caching the computation.
     */
    private Map<BlockPos, BlockState> getPreviewVisibleBlocks(Map<BlockPos, BlockState> clipboardBlocks) {
        if (clipboardBlocks.size() != lastClipboardSize || cachedPreviewVisibleBlocks == null) {
            cachedPreviewVisibleBlocks = computeVisibleBlocks(clipboardBlocks);
            lastClipboardSize = clipboardBlocks.size();
        }
        return cachedPreviewVisibleBlocks;
    }

    /**
     * Computes which blocks have at least one exposed face.
     */
    private Map<BlockPos, BlockState> computeVisibleBlocks(Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, BlockState> visible = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!blocks.containsKey(pos.up()) ||
                !blocks.containsKey(pos.down()) ||
                !blocks.containsKey(pos.north()) ||
                !blocks.containsKey(pos.south()) ||
                !blocks.containsKey(pos.east()) ||
                !blocks.containsKey(pos.west())) {
                visible.put(pos, entry.getValue());
            }
        }
        return visible;
    }

    /**
     * Checks if a block position is within render distance and frustum.
     */
    private boolean isBlockVisible(BlockPos worldPos, Vec3d cameraPos) {
        double dx = worldPos.getX() + 0.5 - cameraPos.x;
        double dy = worldPos.getY() + 0.5 - cameraPos.y;
        double dz = worldPos.getZ() + 0.5 - cameraPos.z;
        if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }

        if (frustum != null) {
            return frustum.isVisible(new Box(worldPos));
        }

        return true;
    }

    private BufferAllocator getOrCreateWireframeBuffer() {
        if (wireframeBuffer == null) {
            wireframeBuffer = new BufferAllocator(WIREFRAME_BUFFER_SIZE);
        }
        return wireframeBuffer;
    }

    private BufferAllocator getOrCreateSolidBuffer(int blockCount) {
        int requiredSize = Math.max(MIN_SOLID_BUFFER_SIZE, blockCount * BYTES_PER_BLOCK);
        if (solidBuffer == null || lastSolidBlockCount * BYTES_PER_BLOCK < requiredSize / 2 ||
            requiredSize > lastSolidBlockCount * BYTES_PER_BLOCK) {
            if (solidBuffer != null) {
                solidBuffer.close();
            }
            solidBuffer = new BufferAllocator(requiredSize);
            lastSolidBlockCount = blockCount;
        }
        return solidBuffer;
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

            BlockPos worldPos = anchor.add(relativePos);

            if (!isBlockVisible(worldPos, cameraPos)) {
                continue;
            }

            int mapColor = state.getMapColor(world, worldPos).color;
            float r = ((mapColor >> 16) & 0xFF) / 255f;
            float g = ((mapColor >> 8) & 0xFF) / 255f;
            float b = (mapColor & 0xFF) / 255f;

            if (isPreview) {
                r = r * 0.7f;
                g = g * 0.7f;
                b = Math.min(1.0f, b * 0.7f + 0.3f);
            }

            float x1 = (float) (worldPos.getX() - cameraPos.x);
            float y1 = (float) (worldPos.getY() - cameraPos.y);
            float z1 = (float) (worldPos.getZ() - cameraPos.z);
            float x2 = x1 + 1;
            float y2 = y1 + 1;
            float z2 = z1 + 1;

            drawBlockOutline(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, opacity);
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

    /**
     * Cleans up all resources.
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

        previewCache = null;
        lockedPlacementCaches.clear();

        cachedPreviewVisibleBlocks = null;
        lastClipboardSize = -1;
        lastPreviewClipboardHash = 0;
        lastPreviewOpacity = -1;
        lastLockedOpacity = -1;
    }

    /**
     * Invalidates all cached vertex data, forcing rebuild on next render.
     * Call when rotation or other settings change that affect the preview.
     */
    public void invalidateCaches() {
        previewCache = null;
        lastPreviewClipboardHash = 0;
        lastPreviewOpacity = -1;

        // Also clear the visible blocks cache (positions change with rotation)
        cachedPreviewVisibleBlocks = null;
        lastClipboardSize = -1;

        lockedPlacementCaches.clear();
        lastLockedOpacity = -1;
    }

    // ========== Inner Classes for Vertex Caching ==========

    /**
     * Stores captured vertex data for replay.
     * Each vertex has: position (3 floats), color (4 ints), texture (2 floats),
     * overlay (2 ints), light (2 ints), normal (3 floats).
     */
    private static class CachedVertexData {
        private final List<CachedVertex> vertices = new ArrayList<>();

        public void addVertex(float x, float y, float z,
                              int r, int g, int b, int a,
                              float u, float v,
                              int overlayU, int overlayV,
                              int lightU, int lightV,
                              float nx, float ny, float nz) {
            vertices.add(new CachedVertex(x, y, z, r, g, b, a, u, v, overlayU, overlayV, lightU, lightV, nx, ny, nz));
        }

        public boolean isEmpty() {
            return vertices.isEmpty();
        }

        /**
         * Replays all cached vertices to the given consumer, transforming positions by the matrix.
         */
        public void replay(VertexConsumer consumer, Matrix4f matrix) {
            for (CachedVertex v : vertices) {
                // Transform position by matrix
                float tx = matrix.m00() * v.x + matrix.m10() * v.y + matrix.m20() * v.z + matrix.m30();
                float ty = matrix.m01() * v.x + matrix.m11() * v.y + matrix.m21() * v.z + matrix.m31();
                float tz = matrix.m02() * v.x + matrix.m12() * v.y + matrix.m22() * v.z + matrix.m32();

                consumer.vertex(tx, ty, tz)
                    .color(v.r, v.g, v.b, v.a)
                    .texture(v.u, v.v)
                    .overlay(v.overlayU, v.overlayV)
                    .light(v.lightU, v.lightV)
                    .normal(v.nx, v.ny, v.nz);
            }
        }
    }

    /**
     * A single cached vertex with all attributes.
     */
    private static record CachedVertex(
        float x, float y, float z,
        int r, int g, int b, int a,
        float u, float v,
        int overlayU, int overlayV,
        int lightU, int lightV,
        float nx, float ny, float nz
    ) {}

    /**
     * VertexConsumerProvider that returns our caching consumer.
     */
    private static class CachingVertexConsumerProvider implements VertexConsumerProvider {
        private final CachingVertexConsumer consumer;

        public CachingVertexConsumerProvider(CachingVertexConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return consumer;
        }
    }

    /**
     * VertexConsumer that captures vertex data into a cache and applies alpha/shading.
     */
    private static class CachingVertexConsumer implements VertexConsumer {
        private final CachedVertexData cache;
        private final int alpha;
        private final boolean isPreview;

        // Current vertex state
        private float x, y, z;
        private int r, g, b;
        private float u, v;
        private int overlayU, overlayV;
        private int lightU, lightV;
        private float nx, ny, nz;
        private boolean hasColor = false;
        private float shade = 1.0f;

        // Minecraft's directional shading values
        private static final float SHADE_DOWN = 0.5f;
        private static final float SHADE_UP = 1.0f;
        private static final float SHADE_NORTH_SOUTH = 0.8f;
        private static final float SHADE_EAST_WEST = 0.6f;

        public CachingVertexConsumer(CachedVertexData cache, float alpha, boolean isPreview) {
            this.cache = cache;
            this.alpha = (int) (alpha * 255) & 0xFF;
            this.isPreview = isPreview;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.r = red;
            this.g = green;
            this.b = blue;
            this.hasColor = true;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.overlayU = u;
            this.overlayV = v;
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.lightU = u;
            this.lightV = v;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.nx = x;
            this.ny = y;
            this.nz = z;

            // Calculate shade based on face normal
            float absX = Math.abs(x);
            float absY = Math.abs(y);
            float absZ = Math.abs(z);

            if (absY >= absX && absY >= absZ) {
                shade = y > 0 ? SHADE_UP : SHADE_DOWN;
            } else if (absX >= absZ) {
                shade = SHADE_EAST_WEST;
            } else {
                shade = SHADE_NORTH_SOUTH;
            }

            // Apply shade and preview tint to color, then commit vertex
            int finalR = r;
            int finalG = g;
            int finalB = b;

            if (hasColor) {
                finalR = (int) (r * shade);
                finalG = (int) (g * shade);
                finalB = (int) (b * shade);

                if (isPreview) {
                    finalR = (int) (finalR * 0.85f);
                    finalG = (int) (finalG * 0.85f);
                    finalB = Math.min(255, (int) (finalB * 0.85f + 38));
                }
            }

            // Store the vertex
            cache.addVertex(this.x, this.y, this.z,
                finalR, finalG, finalB, this.alpha,
                this.u, this.v,
                this.overlayU, this.overlayV,
                this.lightU, this.lightV,
                this.nx, this.ny, this.nz);

            // Reset state for next vertex
            hasColor = false;
            shade = 1.0f;

            return this;
        }
    }
}
