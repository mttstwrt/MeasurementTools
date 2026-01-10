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

import java.util.Set;

/**
 * Renders hollow shape outlines by drawing individual block outlines
 * for each block that forms the surface of the shape.
 */
public class HollowShapeRenderer {
    private BufferAllocator buffer;
    private static final int BUFFER_SIZE = 65536;

    // Cache for computed hollow blocks
    private Set<BlockPos> cachedHollowBlocks;
    private ShapeMode cachedShapeMode;
    private int cachedFilterLayer = -1;
    private int cachedSelectionHash = 0;

    /**
     * Renders the hollow shape using individual block outlines.
     */
    public void render(Camera camera, Matrix4f viewMatrix, ShapeMode mode, ShapeRenderer.RenderConfig config) {
        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) return;

        // Determine filter layer (-1 means show all layers)
        int filterLayer = -1;
        if (manager.isLayerModeEnabled()) {
            filterLayer = manager.getCurrentLayerY();
        }

        // Get the hollow blocks (with caching)
        Set<BlockPos> hollowBlocks = getHollowBlocks(mode, filterLayer, manager);
        if (hollowBlocks.isEmpty()) return;

        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        // Draw outline for each block
        for (BlockPos pos : hollowBlocks) {
            float x1 = (float) (pos.getX() - cameraPos.x);
            float y1 = (float) (pos.getY() - cameraPos.y);
            float z1 = (float) (pos.getZ() - cameraPos.z);
            float x2 = x1 + 1;
            float y2 = y1 + 1;
            float z2 = z1 + 1;

            drawBlockOutline(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        immediate.draw();
    }

    /**
     * Gets hollow blocks with caching to avoid recalculating every frame.
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

        // Recalculate
        cachedHollowBlocks = HollowBlockCalculator.calculateHollowBlocks(mode, filterLayer);
        cachedShapeMode = mode;
        cachedFilterLayer = filterLayer;
        cachedSelectionHash = selectionHash;

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
        return hash;
    }

    /**
     * Invalidates the cache, forcing recalculation on next render.
     */
    public void invalidateCache() {
        cachedHollowBlocks = null;
        cachedSelectionHash = 0;
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

    public void cleanup() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        cachedHollowBlocks = null;
    }
}
