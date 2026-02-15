package measurementtools.modid.render;

import measurementtools.modid.SelectionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class CylinderRenderer implements ShapeRenderer {
    private static final int CIRCLE_SEGMENTS = 32;
    private static final int BUFFER_SIZE = 4096;
    private BufferAllocator buffer;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        SelectionManager manager = SelectionManager.getInstance();
        BlockPos center = manager.getCenterBlock();
        if (center == null) return;

        double radius = manager.getMaxRadiusXZ() + manager.getCylinderRadiusOffsetBlocks();
        if (radius < 0.5) radius = 0.5;

        int minY = manager.getMinY();
        int maxY = manager.getMaxY();
        int height = maxY - minY + 1;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(
            center.getX() + 0.5 - cameraPos.x,
            minY - cameraPos.y,
            center.getZ() + 0.5 - cameraPos.z
        );

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        // Draw bottom circle
        drawCircle(matrices, lines, 0, radius, CIRCLE_SEGMENTS, r, g, b, a);

        // Draw top circle
        drawCircle(matrices, lines, height, radius, CIRCLE_SEGMENTS, r, g, b, a);

        // Draw vertical lines (8 evenly spaced)
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            float x = (float) (Math.cos(angle) * radius);
            float z = (float) (Math.sin(angle) * radius);
            RenderUtils.drawLine(matrix, lines, x, 0, z, x, height, z, r, g, b, a);
        }

        // Draw labels
        if (config.showLabels()) {
            drawLabels(camera, viewMatrix, matrices, radius, height);
        }

        matrices.pop();
        immediate.draw();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty() || !config.showLabels()) return;

        SelectionManager manager = SelectionManager.getInstance();
        BlockPos center = manager.getCenterBlock();
        if (center == null) return;

        double radius = manager.getMaxRadiusXZ() + manager.getCylinderRadiusOffsetBlocks();
        if (radius < 0.5) radius = 0.5;

        int minY = manager.getMinY();
        int maxY = manager.getMaxY();
        int height = maxY - minY + 1;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        matrices.push();
        matrices.translate(
            center.getX() + 0.5 - cameraPos.x,
            minY - cameraPos.y,
            center.getZ() + 0.5 - cameraPos.z
        );

        drawLabels(camera, viewMatrix, matrices, radius, height);

        matrices.pop();
    }

    private void drawLabels(Camera camera, Matrix4f viewMatrix, MatrixStack matrices, double radius, int height) {
        // Height label on the side
        RenderUtils.drawLabel(camera, viewMatrix, matrices, radius + 0.5, height / 2.0, 0,
            String.format("h=%d", height));

        // Radius label on top
        RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, height + 0.5, 0,
            String.format("r=%.1f", radius));

        // Diameter label
        RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, height + 1.0, 0,
            String.format("d=%.1f", radius * 2));
    }

    private void drawCircle(MatrixStack matrices, VertexConsumer lines,
                            float y, double radius, int segments,
                            float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            RenderUtils.drawLine(matrix, lines, x1, y, z1, x2, y, z2, r, g, b, a);
        }
    }
}
