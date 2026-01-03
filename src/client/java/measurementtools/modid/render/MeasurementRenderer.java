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

    private MeasurementRenderer() {
        renderers.put(ShapeMode.RECTANGLE, new RectangleRenderer());
        renderers.put(ShapeMode.CYLINDER, new CylinderRenderer());
        renderers.put(ShapeMode.ELLIPSOID, new EllipsoidRenderer());
    }

    public static MeasurementRenderer getInstance() {
        return INSTANCE;
    }

    public void render(Camera camera, Matrix4f viewMatrix) {
        SelectionManager manager = SelectionManager.getInstance();
        if (!manager.hasSelection()) return;

        List<BlockPos> selection = manager.getSelectedBlocks();
        ShapeMode mode = manager.getShapeMode();
        int subdivisions = manager.getSubdivisionCount();

        ShapeRenderer.RenderConfig config = new ShapeRenderer.RenderConfig(
            1.0f, 0.3f, 0.3f, 1.0f,
            subdivisions,
            true
        );

        // Render the shape
        ShapeRenderer renderer = renderers.get(mode);
        if (renderer != null) {
            renderer.render(camera, viewMatrix, selection, config);
        }

        // Render subdivisions if enabled (only for rectangle mode)
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
