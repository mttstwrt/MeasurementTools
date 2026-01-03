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

public class EllipsoidRenderer implements ShapeRenderer {
    private static final int LAT_SEGMENTS = 12;
    private static final int LONG_SEGMENTS = 24;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        SelectionManager manager = SelectionManager.getInstance();
        BlockPos center = manager.getCenterBlock();
        if (center == null) return;

        double radiusXZ = manager.getMaxRadiusXZ();
        if (radiusXZ < 0.5) radiusXZ = 0.5;

        int minY = manager.getMinY();
        int maxY = manager.getMaxY();
        double radiusY = (maxY - minY + 1) / 2.0;
        if (radiusY < 0.5) radiusY = 0.5;

        double centerY = (minY + maxY + 1) / 2.0;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(2048));
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(
            center.getX() + 0.5 - cameraPos.x,
            centerY - cameraPos.y,
            center.getZ() + 0.5 - cameraPos.z
        );

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Draw latitude lines (horizontal ellipses at different Y levels)
        for (int lat = -LAT_SEGMENTS / 2; lat <= LAT_SEGMENTS / 2; lat++) {
            double phi = Math.PI * lat / LAT_SEGMENTS;
            double y = Math.sin(phi) * radiusY;
            double latRadius = Math.cos(phi) * radiusXZ;

            drawEllipseXZ(matrix, lines, (float) y, latRadius, LONG_SEGMENTS, r, g, b, a);
        }

        // Draw longitude lines (vertical ellipses around the Y axis)
        for (int lon = 0; lon < 8; lon++) {
            double theta = Math.PI * lon / 8;
            drawLongitudeLine(matrix, lines, theta, radiusXZ, radiusY, LAT_SEGMENTS * 2, r, g, b, a);
        }

        // Draw labels
        if (config.showLabels()) {
            RenderUtils.drawLabel(camera, viewMatrix, matrices, radiusXZ + 0.5, 0, 0,
                String.format("rx=%.1f", radiusXZ));
            RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, radiusY + 0.5, 0,
                String.format("ry=%.1f", radiusY));
        }

        matrices.pop();
        immediate.draw();
    }

    private void drawEllipseXZ(Matrix4f matrix, VertexConsumer lines,
                               float y, double radius, int segments,
                               float r, float g, float b, float a) {
        if (radius < 0.01) return;

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

    private void drawLongitudeLine(Matrix4f matrix, VertexConsumer lines,
                                   double theta, double radiusXZ, double radiusY, int segments,
                                   float r, float g, float b, float a) {
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < segments; i++) {
            double phi1 = Math.PI * i / segments - Math.PI / 2;
            double phi2 = Math.PI * (i + 1) / segments - Math.PI / 2;

            float x1 = (float) (Math.cos(phi1) * radiusXZ * cosTheta);
            float y1 = (float) (Math.sin(phi1) * radiusY);
            float z1 = (float) (Math.cos(phi1) * radiusXZ * sinTheta);

            float x2 = (float) (Math.cos(phi2) * radiusXZ * cosTheta);
            float y2 = (float) (Math.sin(phi2) * radiusY);
            float z2 = (float) (Math.cos(phi2) * radiusXZ * sinTheta);

            RenderUtils.drawLine(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }
    }
}
