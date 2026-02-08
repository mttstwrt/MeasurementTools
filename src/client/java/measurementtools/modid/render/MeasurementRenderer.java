package measurementtools.modid.render;

import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.ShapeMode;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MeasurementRenderer {
    private static final MeasurementRenderer INSTANCE = new MeasurementRenderer();

    private final Map<ShapeMode, ShapeRenderer> renderers = new EnumMap<>(ShapeMode.class);
    private final SubdivisionRenderer subdivisionRenderer = new SubdivisionRenderer();
    private final GhostBlockRenderer ghostBlockRenderer = new GhostBlockRenderer();
    private final HollowShapeRenderer hollowShapeRenderer = new HollowShapeRenderer();

    private MeasurementRenderer() {
        renderers.put(ShapeMode.RECTANGLE, new RectangleRenderer());
        renderers.put(ShapeMode.CYLINDER, new CylinderRenderer());
        renderers.put(ShapeMode.ELLIPSOID, new EllipsoidRenderer());
        renderers.put(ShapeMode.SPLINE, new SplineRenderer());
    }

    public static MeasurementRenderer getInstance() {
        return INSTANCE;
    }

    public void render(Camera camera, Matrix4f viewMatrix) {
        SelectionManager manager = SelectionManager.getInstance();

        // Render selection shapes if there's a selection
        if (manager.hasSelection()) {
            List<BlockPos> selection = manager.getSelectedBlocks();
            ShapeMode mode = manager.getShapeMode();
            int subdivisions = manager.getSubdivisionCount();

            ShapeRenderer.RenderConfig config = new ShapeRenderer.RenderConfig(
                1.0f, 0.3f, 0.3f, 1.0f,
                subdivisions,
                true
            );

            // Check if hollow mode is enabled
            if (manager.isHollowMode()) {
                // Render hollow shape (individual block outlines)
                hollowShapeRenderer.render(camera, viewMatrix, mode, config);

                // Also render the shape-specific labels from the underlying renderer
                ShapeRenderer renderer = renderers.get(mode);
                if (renderer != null) {
                    renderer.renderLabels(camera, viewMatrix, selection, config);
                }
            } else {
                // Render normal wireframe shape
                ShapeRenderer renderer = renderers.get(mode);
                if (renderer != null) {
                    renderer.render(camera, viewMatrix, selection, config);
                }

                // Render subdivisions if enabled (only for rectangle mode, not in hollow mode)
                if (subdivisions > 1 && mode == ShapeMode.RECTANGLE) {
                    BlockPos minPos = manager.getMinPos();
                    BlockPos maxPos = manager.getMaxPos();
                    if (minPos != null && maxPos != null) {
                        Box bounds = new Box(
                            minPos.getX(), minPos.getY(), minPos.getZ(),
                            maxPos.getX() + 1, maxPos.getY() + 1, maxPos.getZ() + 1
                        );
                        subdivisionRenderer.renderSubdivisions(camera, viewMatrix, bounds, subdivisions, 1.0f);
                    }
                }
            }
        }

        // Render ghost blocks (paste preview and locked placements) - always render
        ghostBlockRenderer.render(camera, viewMatrix);
    }

    /**
     * Invalidates ghost block render caches.
     * Call when rotation or other settings change that affect the preview.
     */
    public void invalidateGhostBlockCaches() {
        ghostBlockRenderer.invalidateCaches();
    }

    /**
     * Invalidates hollow shape render cache.
     * Call when selection or layer changes.
     */
    public void invalidateHollowShapeCache() {
        hollowShapeRenderer.invalidateCache();
    }
}
