package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Renders hollow shape outlines by drawing individual block outlines
 * for each block that forms the surface of the shape.
 *
 * Uses CPU-side vertex caching for performance - line vertices are computed once
 * and cached, then replayed each frame with camera-relative transformation.
 */
public class HollowShapeRenderer {
    private BufferAllocator buffer;
    private static final int BUFFER_SIZE = 65536;

    // Cache for computed hollow blocks
    private Set<BlockPos> cachedHollowBlocks;
    private ShapeMode cachedShapeMode;
    private int cachedFilterLayer = -1;
    private int cachedSelectionHash = 0;

    // Cached line vertex data
    private List<CachedLine> cachedLines;
    private boolean linesCacheDirty = true;

    // Anchor point for cached vertices (center of bounding box)
    private double anchorX, anchorY, anchorZ;

    // Cached render config for detecting color changes
    private float cachedRed, cachedGreen, cachedBlue, cachedAlpha;

    /**
     * Renders the hollow shape using individual block outlines.
     * Uses cached vertex data for improved performance.
     */
    public void render(Camera camera, Matrix4f viewMatrix, ShapeMode mode, ShapeRenderer.RenderConfig config) {
        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) return;

        // Determine filter layer (-1 means show all layers)
        int filterLayer = -1;
        if (manager.isLayerModeEnabled()) {
            filterLayer = manager.getCurrentLayerY();
        }

        // Get the hollow blocks (with caching) - this also marks lines cache dirty if blocks changed
        Set<BlockPos> hollowBlocks = getHollowBlocks(mode, filterLayer, manager);
        if (hollowBlocks.isEmpty()) return;

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        // Check if color changed
        if (r != cachedRed || g != cachedGreen || b != cachedBlue || a != cachedAlpha) {
            linesCacheDirty = true;
            cachedRed = r;
            cachedGreen = g;
            cachedBlue = b;
            cachedAlpha = a;
        }

        // Rebuild line cache if needed
        if (linesCacheDirty) {
            rebuildLineCache(hollowBlocks, r, g, b, a);
            linesCacheDirty = false;
        }

        // Render from cache
        if (cachedLines != null && !cachedLines.isEmpty()) {
            renderFromCache(camera, viewMatrix);
        }
    }

    /**
     * Rebuilds the cached line data for all hollow blocks.
     */
    private void rebuildLineCache(Set<BlockPos> hollowBlocks, float r, float g, float b, float a) {
        cachedLines = new ArrayList<>();

        if (hollowBlocks.isEmpty()) return;

        // Calculate anchor point (center of bounding box)
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : hollowBlocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        anchorX = (minX + maxX + 1) / 2.0;
        anchorY = (minY + maxY + 1) / 2.0;
        anchorZ = (minZ + maxZ + 1) / 2.0;

        // Build all block outlines relative to anchor
        for (BlockPos pos : hollowBlocks) {
            float x1 = (float) (pos.getX() - anchorX);
            float y1 = (float) (pos.getY() - anchorY);
            float z1 = (float) (pos.getZ() - anchorZ);
            float x2 = x1 + 1;
            float y2 = y1 + 1;
            float z2 = z1 + 1;

            addBlockOutlineToCache(x1, y1, z1, x2, y2, z2, r, g, b, a);
        }
    }

    /**
     * Renders the cached lines with current camera transform.
     */
    private void renderFromCache(Camera camera, Matrix4f viewMatrix) {
        Vec3d cameraPos = camera.getPos();

        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        // Translate to anchor position (camera-relative)
        matrices.translate(anchorX - cameraPos.x, anchorY - cameraPos.y, anchorZ - cameraPos.z);

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Replay all cached lines
        for (CachedLine line : cachedLines) {
            RenderUtils.drawLine(matrix, lines,
                line.x1, line.y1, line.z1,
                line.x2, line.y2, line.z2,
                line.r, line.g, line.b, line.a);
        }

        immediate.draw();
    }

    /**
     * Adds a block outline (12 edges) to the cache.
     */
    private void addBlockOutlineToCache(float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float r, float g, float b, float a) {
        // Bottom face edges
        cachedLines.add(new CachedLine(x1, y1, z1, x2, y1, z1, r, g, b, a));
        cachedLines.add(new CachedLine(x1, y1, z2, x2, y1, z2, r, g, b, a));
        cachedLines.add(new CachedLine(x1, y1, z1, x1, y1, z2, r, g, b, a));
        cachedLines.add(new CachedLine(x2, y1, z1, x2, y1, z2, r, g, b, a));

        // Top face edges
        cachedLines.add(new CachedLine(x1, y2, z1, x2, y2, z1, r, g, b, a));
        cachedLines.add(new CachedLine(x1, y2, z2, x2, y2, z2, r, g, b, a));
        cachedLines.add(new CachedLine(x1, y2, z1, x1, y2, z2, r, g, b, a));
        cachedLines.add(new CachedLine(x2, y2, z1, x2, y2, z2, r, g, b, a));

        // Vertical edges
        cachedLines.add(new CachedLine(x1, y1, z1, x1, y2, z1, r, g, b, a));
        cachedLines.add(new CachedLine(x2, y1, z1, x2, y2, z1, r, g, b, a));
        cachedLines.add(new CachedLine(x1, y1, z2, x1, y2, z2, r, g, b, a));
        cachedLines.add(new CachedLine(x2, y1, z2, x2, y2, z2, r, g, b, a));
    }

    /**
     * Gets hollow blocks with caching to avoid recalculating every frame.
     * Also marks line cache as dirty when blocks change.
     */
    private Set<BlockPos> getHollowBlocks(ShapeMode mode, int filterLayer, SelectionManager manager) {
        // Compute a hash of the selection state to detect changes
        int selectionHash = computeSelectionHash(manager);

        // Check if cache is valid
        if (cachedHollowBlocks != null &&
            cachedShapeMode == mode &&
            cachedFilterLayer == filterLayer &&
            cachedSelectionHash == selectionHash) {
            return cachedHollowBlocks;
        }

        // Recalculate - blocks changed, so line cache needs rebuild
        cachedHollowBlocks = HollowBlockCalculator.calculateHollowBlocks(mode, filterLayer);
        cachedShapeMode = mode;
        cachedFilterLayer = filterLayer;
        cachedSelectionHash = selectionHash;
        linesCacheDirty = true;

        return cachedHollowBlocks;
    }

    /**
     * Computes a hash of the selection state to detect changes.
     */
    private int computeSelectionHash(SelectionManager manager) {
        BlockPos min = manager.getMinPos();
        BlockPos max = manager.getMaxPos();
        if (min == null || max == null) return 0;

        int hash = 17;
        hash = 31 * hash + min.hashCode();
        hash = 31 * hash + max.hashCode();
        hash = 31 * hash + manager.getSelectedBlocks().size();
        hash = 31 * hash + (manager.getEllipsoidMode() != null ? manager.getEllipsoidMode().hashCode() : 0);
        hash = 31 * hash + manager.getSplineRadius();
        return hash;
    }

    /**
     * Invalidates the cache, forcing recalculation on next render.
     */
    public void invalidateCache() {
        cachedHollowBlocks = null;
        cachedSelectionHash = 0;
        linesCacheDirty = true;
    }

    /**
     * Cleans up resources. Should be called when renderer is no longer needed.
     */
    public void cleanup() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        cachedHollowBlocks = null;
        cachedLines = null;
        linesCacheDirty = true;
    }

    /**
     * Cached line segment data.
     */
    private static record CachedLine(
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float r, float g, float b, float a
    ) {}
}
